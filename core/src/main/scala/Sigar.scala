//: ----------------------------------------------------------------------------
//: Copyright (C) 2015 Verizon.  All Rights Reserved.
//:
//:   Licensed under the Apache License, Version 2.0 (the "License");
//:   you may not use this file except in compliance with the License.
//:   You may obtain a copy of the License at
//:
//:       http://www.apache.org/licenses/LICENSE-2.0
//:
//:   Unless required by applicable law or agreed to in writing, software
//:   distributed under the License is distributed on an "AS IS" BASIS,
//:   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//:   See the License for the specific language governing permissions and
//:   limitations under the License.
//:
//: ----------------------------------------------------------------------------
package funnel

import java.util.concurrent.{ExecutorService, ScheduledExecutorService}
import collection.JavaConversions._
import scala.concurrent.duration._
import scalaz.concurrent.Strategy
import scalaz.stream._, time.awakeEvery
import journal.Logger

/** Functions for adding various system metrics collected by SIGAR to a `Monitoring` instance. */
class Sigar(I: Instruments, sigar: org.hyperic.sigar.Sigar) {
  val log = Logger[this.type]

  type G = Gauge[Periodic[Stats], Double]

  import I._

  case class CpuStats(user: G,
                      idle: G,
                      total: G,
                      sys: G,
                      waiting: G)

  case class FileSystemStats(usePercent: G)

  object CPU {
    def label(s: String) = s"system/cpu/$s"

    def cpuUsage(label: String => String): CpuStats = {
      def pct(s: String, desc: String) =
        numericGauge(label(s), 0.0, Units.Ratio, desc)

      CpuStats(pct("user", "Usage of CPU by user code"),
               pct("idle", "CPU idle percentage"),
               pct("combined", "Combined CPU usage"),
               pct("sys", "Usage of CPU by system code"),
               pct("wait", "CPU usage waiting for I/O"))
    }

    object Aggregate {
      def label(s: String) = CPU.label(s"aggregate/$s")

      val usage = cpuUsage(s => label(s"usage/$s"))
    }

  }

  object Mem {
    def label(s: String) = s"system/mem/$s"
    def mem(s: String, desc: String)
      = numericGauge(label(s), 0.0, Units.Bytes(Units.Base.Zero), desc)
    def MB(s: String, desc: String) =
      numericGauge(label(s), 0.0, Units.Megabytes, desc)
    def pct(s: String, desc: String) =
      numericGauge(label(s), 0.0, Units.Ratio, desc)

    val usedPercent = pct("used_percent", "Ratio of used to free system memory")
  }

  object FileSystem {
    val fileSystems = sigar.getFileSystemList
    val usages = fileSystems.map { d =>
      val dirName = java.net.URLEncoder.encode(d.getDirName, "UTF-8")
      def label(s: String) = s"system/file_system/${dirName}/$s"
      def mem(s: String, desc: String) =
        numericGauge(label(s), 0.0, Units.Bytes(Units.Base.Zero), desc)
      def pct(s: String, desc: String) =
        numericGauge(label(s), 0.0, Units.Ratio, desc)
      def num(s: String, desc: String) =
        numericGauge(label(s), 0.0, Units.Count, desc)
      def ms(s: String, desc: String) =
        numericGauge(label(s), 0.0, Units.Milliseconds, desc)

      (d.getDirName, FileSystemStats(
        usePercent = pct("use_percent", "Percent of disk used")
      ))
    }.toMap
  }

  object LoadAverage {
    val one = numericGauge("system/load_average/1", 0.0, Units.Count,
      "1-minute load average")
    val five = numericGauge("system/load_average/5", 0.0, Units.Count,
      "5-minute load average")
    val fifteen = numericGauge("system/load_average/15", 0.0, Units.Count,
      "15-minute load average")
  }

  val Uptime = numericGauge("system/uptime", 0.0, Units.Seconds, "System uptime")

  object TCP {
    def label(s: String) = s"system/tcp/$s"
    def num(s: String, desc: String) = numericGauge(label(s), 0.0, Units.Count, desc)
    val currEstab = num("curr_estab", "Number of open sockets")
    val passiveOpens = num("passive_opens", "Number of open server connections")
    val attemptFails = num("attempt_fails", "Number of failed client connections")
  }

  def instrument(
    implicit ES: ExecutorService = Monitoring.serverPool,
             TS: ScheduledExecutorService = Monitoring.schedulingPool,
             t: Duration = 10.seconds): Unit = {

    // Make the side effects happen.
    // Side effects FTL!
    val _ = (TCP, LoadAverage, FileSystem, CPU.Aggregate, Mem)

    awakeEvery(t)(Strategy.Executor(ES), TS).map { _ =>
      import org.hyperic.sigar.{Cpu, CpuPerc}

      def cpuUsage(cpu: CpuPerc, m: CpuStats) = {
        m.user.set(cpu.getUser)
        m.idle.set(cpu.getIdle)
        m.total.set(cpu.getCombined)
        m.sys.set(cpu.getSys)
        m.waiting.set(cpu.getWait)
      }

      // Add aggregate CPU usage
      cpuUsage(sigar.getCpuPerc, CPU.Aggregate.usage)

      // Add per-filesystem usages
      FileSystem.usages.foreach { case (k, v) =>
        try {
          // Getting the mounted file systems so we don't hang
          // if NFS volumes are unreachable
          val u = sigar.getMountedFileSystemUsage(k)
          v.usePercent.set(u.getUsePercent)
        } catch {
          // Catch exceptions caused by file systems getting dismounted etc.
          case e: Exception => log.error(e.toString)
        }
      }

      // Add memory stats
      val mem = sigar.getMem
      Mem.usedPercent.set(mem.getUsedPercent)

      // Add uptime
      Uptime.set(sigar.getUptime.getUptime)

      // Add load averages
      val loads = sigar.getLoadAverage
      import LoadAverage._
      List(one, five, fifteen).zip(loads).foreach {
        case (g, d) => g.set(d)
      }

      // Add TCP stats
      val tcp = sigar.getTcp
      TCP.currEstab.set(tcp.getCurrEstab)
      TCP.passiveOpens.set(tcp.getPassiveOpens)
      TCP.attemptFails.set(tcp.getAttemptFails)

    }.run.attempt.runAsync(_.fold(
      x => log.info("Sigar monitoring terminated normally with $x"),
      x => log.error("Sigar monitoring terminated abnormally with $x")))
  }
}

object Sigar {
  import org.hyperic.sigar.SigarException
  val log = Logger[this.type]

  def apply(I: Instruments): Option[Sigar] = {
    try {
      val sigar = new org.hyperic.sigar.Sigar
      // internals of sigar seem to be lazy, so lets force a
      // LinkageError if there is going to be one.
      // Did I mention that I *hate* native libraries....
      sigar.getPid

      Option(new Sigar(I, sigar))
    } catch {
      case e: SigarException =>
        log.error(s"Unable to initilize Sigar: $e")
        None
      case e: LinkageError   =>
        log.warn("Unable to load native Sigar library, it may not be installed?")
        log.warn(s"The following error was encountered: $e")
        log.warn("java.library.path is set to: " +
          System.getProperty("java.library.path"))
        None
    }
  }

  def instrument(I: Instruments): Unit =
    apply(I).foreach(_.instrument)

}
