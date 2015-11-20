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

import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}
import scalaz.concurrent.Task
import scalaz.syntax.monad._

/**
 * A `LapTimer` instrument is a compound instrument, which combines Timer and
 * Counter instruments. The counter counts the timer operations.
 *
 * A `LapTimer` should be constructed using the [[Instruments.lapTimer]] method.
 */
class LapTimer (
  timer: Timer[Periodic[Stats]],
  counter: Counter
) {

  /** UNSAFE. Record the given duration, in nanoseconds, and advance the lap counter. */
  def recordNanos(nanos: Long): Unit = postNanos(nanos).runAsync(_ => ())

  /** Record the given duration, in nanoseconds, and advance the lap counter. */
  def postNanos(nanos: Long): Task[Unit] =
    counter.advance >> timer.postNanos(nanos)

  /** UNSAFE. Record the given duration. */
  def record(d: Duration): Unit = post(d: Duration).runAsync(_ => ())

  def post(d: Duration): Task[Unit] =
    counter.advance >> timer.post(d)

  /**
   * UNSAFE. Return a newly running stopwatch.
   * See `Timer.start`
   */
  def start: () => Unit = {
    counter.increment
    timer.start
  }

  /**
   * Return a newly running stopwatch.
   * See `Timer.startClock`
   */
  def startClock: Task[Timer.Clock] =
    counter.advance >> timer.startClock

  /** UNSAFE. A bit of syntax for stopping a stopwatch returned from `start`. */
  def stop(stopwatch: () => Unit): Unit = {
    timer.stop(stopwatch)
  }

  /**
   * UNSAFE. Evaluate `a` and record its evaluation time even if
   * evaluation completes with an error. Use `timeSuccess`
   * if you'd like to record a time only in the sucsessful case.
   */
  def time[A](a: => A): A =
    measure(a).run

  /**
   * Evaluate `a` and record its evaluation time even if
   * evaluation completes with an error. Use `timeSuccess`
   * if you'd like to record a time only in the sucsessful case.
   */
  def measure[A](a: => A): Task[A] =
    counter.advance >> timer.measure(a)

  /**
   * UNSAFE. Like `time`, but records a time only if evaluation of
   * `a` completes without error.
   */
  def timeSuccess[A](a: => A): A =
    measureSuccess(a).run

  /**
   * Like `time`, but records a time only if evaluation of
   * `a` completes without error.
   */
  def measureSuccess[A](a: => A): Task[A] =
    counter.advance >> timer.measureSuccess(a)

  /**
   * UNSAFE. Time a `Future` by registering a callback on its
   * `onComplete` method. The stopwatch begins now.
   * This function records a time regardless if the `Future`
   * completes with an error or not. Use `timeFutureSuccess` or
   * explicit calls to `start` and `stop` if you'd like to
   * record a time only in the event the `Future` succeeds.
   */
  def timeFuture[A](f: Future[A])(implicit ctx: ExecutionContext = ExecutionContext.Implicits.global): Future[A] = {
    measureFuture(f).runAsync(_ => ())
    f
  }

  /**
   * Time a `Future` by registering a callback on its
   * `onComplete` method. The stopwatch begins now.
   * This function records a time regardless if the `Future`
   * completes with an error or not. Use `timeFutureSuccess` or
   * explicit calls to `start` and `stop` if you'd like to
   * record a time only in the event the `Future` succeeds.
   */
  def measureFuture[A](f: Future[A])(implicit ctx: ExecutionContext = ExecutionContext.Implicits.global): Task[A] =
    counter.advance >> timer.measureFuture(f)

  /**
   * UNSAFE. Like `timeFuture`, but records a time only if `f` completes
   * without an exception.
   */
  def timeFutureSuccess[A](f: Future[A])(
    implicit ctx: ExecutionContext = ExecutionContext.Implicits.global): Future[A] = {
      measureFutureSuccess(f).runAsync(_ => ())
      f
    }

  /**
   * UNSAFE. Like `measureFuture`, but records a time only if `f` completes
   * without an exception.
   */
  def measureFutureSuccess[A](f: Future[A])(
    implicit ctx: ExecutionContext = ExecutionContext.Implicits.global): Task[A] =
      counter.advance >> timer.measureFutureSuccess(f)

  /**
   * Time an asynchronous `Task`. The stopwatch begins running when
   * the returned `Task` is run and a stop time is recorded if the
   * `Task` completes in any state. Also advances the counter whether
   * the task succeeds or not. Use `timeTaskSuccess` if you
   * wish to only record times when the `Task` succeeds.
   */
  def timeTask[A](a: Task[A]): Task[A] =
    timer.timeTask(a).onFinish(_ => counter.advance)

  /**
   * Like `timeTask`, but records a time only if the `Task` completes
   * without an error.
   */
  def timeTaskSuccess[A](a: Task[A]): Task[A] =
    timer.timeTaskSuccess(a) <* counter.advance

  /**
   * UNSAFE. Time a currently running asynchronous task. The
   * stopwatch begins now, and finishes when the
   * callback is invoked with the result.
   */
  def timeAsync[A](register: (A => Unit) => Unit): Unit =
    measureAsync(register).runAsync(_ => ())

  /**
   * Time an asynchronous task. The stopwatch begins
   * when the returned `Task` is run, and finishes when the
   * callback is invoked with the result.
   */
  def measureAsync[A](register: (A => Unit) => Unit): Task[A] =
    counter.advance *> timer.measureAsync(register)

}
