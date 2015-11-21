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

import java.util.concurrent.{Executors, ExecutorService, ScheduledExecutorService}
import scala.concurrent.duration._
import scalaz.concurrent.{Strategy, Task}
import scalaz.stream.Process
import scalaz.stream.time.awakeEvery
import scalaz.Monoid

object Events {

  /**
   * A discrete, infinite stream of 'ticks', typically used
   * to control scheduling of some other action.
   */
  type Event = Monitoring => Process[Task, Unit]

  implicit def eventMonoid = new Monoid[Event] {
    def append(x: Event, y: => Event) = or(x, y)
    def zero = _ => Process.halt
  }

  val P = Strategy.Executor(Executors.newCachedThreadPool)

  /**
   * An event which fires at the supplied regular interval.
   * Because this drives the schedule for attemptRepeatedly(),
   * it uses its own cached thread pool. This is to accomodate
   * catastrophic retry scenarios, e.g. when 50+ endpoints
   * unexpectedly vanish from a Flask.
   */
  def every(d: Duration)(
    implicit schedulingPool: ScheduledExecutorService = Monitoring.schedulingPool):
      Event = _ => awakeEvery(d)(P, schedulingPool).map(_ => ())

  /**
   * The first `n` ticks of an event which fires at the supplied
   * regular interval.
   */
  def takeEvery(d: Duration, n: Int)(
    implicit pool: ExecutorService = Monitoring.defaultPool,
    schedulingPool: ScheduledExecutorService = Monitoring.schedulingPool):
    Event = every(d) andThen (_.take(n))

  /** An event which fires whenever the given `Key` is updated. */
  def changed[A](k: Key[A]): Event = m => m.get(k).changes

  /** An event which fires whenever either event fires. */
  def or(e1: Event, e2: Event): Event =
    m => e1(m).merge(e2(m))(P)

  /** An event which waits for both events to fire. */
  def and(e1: Event, e2: Event): Event =
    m => {
      val s1 = e1(m)
      val s2 = e2(m)
      s1.flatMap(_ => s2.once)
    }

  // def exponentialBackoff(failure: Signal[Boolean], base: Duration): Event
  // def exponentialBackoff(failure: Key[Boolean], base: Duration): Event
}

