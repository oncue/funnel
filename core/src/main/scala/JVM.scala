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

import java.lang.management.GarbageCollectorMXBean
import java.lang.management.ManagementFactory
import java.util.concurrent.{ExecutorService, ScheduledExecutorService}
import collection.JavaConversions._
import scala.concurrent.duration._
import scalaz.concurrent.Strategy
import scalaz.stream._

/** Functions for adding various JVM metrics to a `Monitoring` instance. */
object JVM {

  /**
   * Add various JVM metrics to a `Monitoring` instance.
   */
  def instrument(I: Instruments)(
    implicit ES: ExecutorService = Monitoring.defaultPool,
             TS: ScheduledExecutorService = Monitoring.schedulingPool,
             t: Duration = 30 seconds): Unit = {

    val mxBean = ManagementFactory.getMemoryMXBean
    val gcs = ManagementFactory.getGarbageCollectorMXBeans.toList
    val pools = ManagementFactory.getMemoryPoolMXBeans.toList

    def threadCount(s: Thread.State): Int =
      Thread.getAllStackTraces.keySet.toList.map(
        _.getState).filter(_ == s).length

    val ST = Strategy.Executor(ES)

    import I._
    gcs.foreach { gc =>
      val name = gc.getName.replace(' ', '-')
      val numCollections = numericGauge(s"jvm/gc/$name", 0, Units.Count)
      val collectionTime = numericGauge(s"jvm/gc/$name/time", 0, Units.Milliseconds)
      time.awakeEvery(t)(ST,TS).map { _ =>
        numCollections.set(gc.getCollectionCount.toDouble)
        collectionTime.set(gc.getCollectionTime.toDouble)
      }.run.runAsync(_ => ())
    }

    def TC(state: Thread.State) =
      numericGauge(s"jvm/threads/${state.toString.toLowerCase}", 0d)

    def MB(lbl: String, desc: String): Gauge[Periodic[Stats], Double] =
      Gauge.scale(1/1e6)(numericGauge(lbl, 0.0, Units.Megabytes, desc))

    val newThreads = TC(Thread.State.NEW)
    val runnableThreads = TC(Thread.State.RUNNABLE)
    val blockedThreads = TC(Thread.State.BLOCKED)
    val waitingThreads = TC(Thread.State.WAITING)
    val timedWaitingThreads = TC(Thread.State.TIMED_WAITING)
    val terminatedThreads = TC(Thread.State.TERMINATED)

    val totalInit = MB("jvm/memory/total/init",
                       "The amount of memory that the JVM initially requests from the operating system for memory management.")
    val totalUsed = MB("jvm/memory/total/used",
                       "The amount of used memory.")
    val totalMax = MB("jvm/memory/total/max",
                      "The maximum amount of memory that can be used for memory management.")
    val totalCommitted = MB("jvm/memory/total/committed",
                            "The amount of memory that is committed for the JVM to use.")

    val heapInit = MB("jvm/memory/heap/init",
                      "The amount of heap memory that the JVM initially requests from the operating system.")
    val heapUsed = MB("jvm/memory/heap/used",
                      "The amount of used heap memory.")
    val heapUsage = numericGauge("jvm/memory/heap/usage", 0.0, Units.Ratio,
                                 "Ratio of heap memory in use.")
    val heapMax = MB("jvm/memory/heap/max",
                     "The maximum amount of heap memory that can be used for memory management.")
    val heapCommitted = MB("jvm/memory/heap/committed",
                           "The amount of heap memory that is committed for the JVM to use.")

    val nonheapInit = MB("jvm/memory/nonheap/init",
                         "The amount of nonheap memory that the JVM initially requests from the operating system for memory management.")
    val nonheapUsed = MB("jvm/memory/nonheap/used",
                         "The amount of used nonheap memory.")
    val nonheapUsage = numericGauge("jvm/memory/nonheap/usage", 0.0, Units.Ratio,
                                    "Ratio of nonheap memory in use.")
    val nonheapMax = MB("jvm/memory/nonheap/max",
                         "The maximum amount of nonheap memory that can be used for memory management.")
    val nonheapCommitted = MB("jvm/memory/nonheap/committed",
                              "The amount of nonheap memory that is committed for the JVM to use.")

    time.awakeEvery(t)(ST,TS).map { _ =>
      import mxBean.{getHeapMemoryUsage => heap, getNonHeapMemoryUsage => nonheap}
      totalInit.set(heap.getInit + nonheap.getInit)
      totalUsed.set(heap.getUsed + nonheap.getUsed)
      totalMax.set(heap.getMax + nonheap.getMax)
      totalCommitted.set(heap.getCommitted + nonheap.getCommitted)
      heapInit.set(heap.getInit)
      heapUsed.set(heap.getUsed)
      heapUsage.set(heap.getUsed.toDouble / heap.getMax)
      heapMax.set(heap.getMax)
      heapCommitted.set(heap.getCommitted)
      nonheapInit.set(nonheap.getInit)
      nonheapUsed.set(nonheap.getUsed)
      nonheapUsage.set(nonheap.getUsed.toDouble / nonheap.getMax)
      nonheapMax.set(nonheap.getMax)
      nonheapCommitted.set(nonheap.getCommitted)

      newThreads.set(threadCount(Thread.State.NEW))
      runnableThreads.set(threadCount(Thread.State.RUNNABLE))
      blockedThreads.set(threadCount(Thread.State.BLOCKED))
      waitingThreads.set(threadCount(Thread.State.WAITING))
      timedWaitingThreads.set(threadCount(Thread.State.TIMED_WAITING))
      terminatedThreads.set(threadCount(Thread.State.TERMINATED))
    }.run.runAsync(_ => ())
  }
}
