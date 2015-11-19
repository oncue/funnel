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
package flask

import scala.concurrent.duration._
import scalaz.concurrent.Strategy
import scalaz.concurrent.Task
import scalaz.std.option._
import scalaz.syntax.applicative._
import scalaz.stream.{ io, Process, Sink, channel }
import scalaz.stream.async.mutable.{Queue,Signal}
import knobs.{Config, Required, ClassPathResource, FileResource}
import journal.Logger
import funnel.{Events,DatapointParser,Datapoint,Names,Sigar,Monitoring,Instruments}
import funnel.http.{MonitoringServer,SSE}
import funnel.elastic._
import funnel.zeromq.Mirror
import java.net.URI
import zeromq._
import sockets._
import scalaz.stream._
import scalaz.\/

/**
 *
 */
class Flask(options: Options, val I: Instruments) {
  import Events.Event
  import scalaz.\/._

  val log = Logger[this.type]

  val Selfie = Monitoring.instance
  val ISelfie = new Instruments(Selfie)
  val mirrorDatapoints = ISelfie.counter("mirror/datapoints")

  val S = MonitoringServer.start(I.monitoring, options.funnelPort)
  val SelfServing = MonitoringServer.start(ISelfie.monitoring, options.selfiePort)

  lazy val signal: Signal[Boolean] = scalaz.stream.async.signalOf(true)(Strategy.Executor(Monitoring.serverPool))

  private[funnel] def shutdown(): Unit = {
    S.stop()
    SelfServing.stop()
    signal.set(false).flatMap(_ => signal.close).run
  }

  private def runAsync(p: Task[Unit]): Unit = p.runAsync(_.fold(e => {
    e.printStackTrace()
    log.error(s"[FATAL]: error in runAsync(): error=$e msg=${e.getMessage}")
    log.error(e.getStackTrace.toList.mkString("\n","\t\n",""))
  }, identity _))

  private def httpOrZmtp(alive: Signal[Boolean])(uri: URI): Process[Task,Datapoint[Any]] =
    Option(uri.getScheme).map(_.toLowerCase) match {
      case Some("http")       => SSE.readEvents(uri)
      case Some("zeromq+tcp") => Mirror.from(alive)(uri)
      case unknown            => Process.fail(
        new RuntimeException(s"Unknown URI scheme submitted. scheme = $unknown"))
    }

  def unsafeRun(): Unit = {

    def countDatapoints: Sink[Task, Datapoint[Any]] =
      channel.lift(_ => Task(mirrorDatapoints.increment))

    // Determine whether to generate system statistics for the local host
    for {
      b <- options.collectLocalMetrics
      t <- options.localMetricFrequency
    }{
      implicit val duration = t.seconds
      Sigar(ISelfie).foreach(_.instrument)
      JVM.instrument(ISelfie)
    }

    def processDatapoints(alive: Signal[Boolean])(uri: URI): Process[Task, Datapoint[Any]] =
      httpOrZmtp(alive)(uri) observe countDatapoints

    def retries(names: Names): Event = {
      val retries = Events.takeEvery(options.retriesDuration, options.maxRetries)

      retries andThen (_ ++ Process.eval[Task, Unit]{
        Task.delay(log.error("stopped mirroring: " + names.toString))
      })
    }

    val flaskHost = java.net.InetAddress.getLocalHost.getHostName
    val flaskName = options.name.getOrElse(flaskHost)
    val flaskCluster = options.cluster.getOrElse(s"flask-${BuildInfo.version}")

    options.metricTTL.foreach { t =>
      log.info("Booting the key senescence...")
      runAsync(I.monitoring.keySenescence(Events.every(t), I.monitoring.distinctKeys).run)
    }

    log.info("Booting the mirroring process...")
    runAsync(I.monitoring.processMirroringEvents(processDatapoints(signal), flaskName, retries))

    log.info("Mirroring my own monitoring server instance...")
    List(s"http://$flaskHost:${options.selfiePort}/stream/previous",
         s"http://$flaskHost:${options.selfiePort}/stream/now?kind=traffic").foreach { s =>
      I.monitoring.mirroringQueue.enqueueOne(funnel.Mirror(new URI(s), flaskCluster)).run
    }

    options.elasticExploded.foreach { elastic =>
      log.info("Booting the elastic-exploded search sink...")
      runAsync(ElasticExploded(I.monitoring).publish(flaskName, flaskCluster)(elastic))
    }

    options.elasticFlattened.foreach { elastic =>
      log.info("Booting the elastic-flattened search sink...")
      runAsync(ElasticFlattened(I.monitoring).publish(options.environment, flaskName, flaskCluster)(elastic))
    }
  }
}
