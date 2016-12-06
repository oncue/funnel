package scalaz.stream.async.mutable

import funnel._

import scalaz.concurrent.Strategy

//ideally this needs to be fixed in scalaz but until proper solution is in place we collect workarounds here
object ScalazHack {
  /*
      This will create circular buffer that will report its current utilization and when content is dropped.
      Unfortunately Queue object is private in scalaz and we have to use this workaround
   */
  def observableCircularBuffer[A](bound: Int, droppedCounter: Counter, currentSize: Gauge[Periodic[Stats], Double])
                                 (S: Strategy): Queue[A] = {
    val checkAndReport: (Seq[A], Vector[A]) => Vector[A] =
      (as, q) => {
        val sz = q.size
        val overflow = as.size + sz - bound
        if (overflow > 0) {
          currentSize.set(bound)
          droppedCounter.incrementBy(overflow)
          q.drop(overflow)
        } else {
          currentSize.set(sz)
          q
        }
      }

    Queue.mk(bound, recover=false, checkAndReport)(S)
  }
}
