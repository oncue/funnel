package funnel

import java.util.concurrent.atomic._
import java.util.concurrent.TimeUnit
import java.util.concurrent.{ConcurrentLinkedQueue, ExecutorService, ScheduledExecutorService}
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext,Future}
import scalaz.concurrent.Strategy
import scalaz.concurrent.Task
import scalaz.\/
import scalaz.\/._
import scalaz.syntax.monad._


trait Timer[K] extends Instrument[K] { self =>

  /** UNSAFE. Record the given duration, in nanoseconds. */
  def recordNanos(nanos: Long): Unit =
    postNanos(nanos).run

  def record(d: Duration): Unit = recordNanos(d.toNanos)

  /** Record the given duration, in nanoseconds. */
  def postNanos(nanos: Long): Task[Unit]

  /** Record the given duration. */
  def post(d: Duration): Unit = postNanos(d.toNanos)

  /**
   * UNSAFE. Returns a newly running clock. To record
   * a time, call the returned clock. Example:
   *
   *    val T: Timer = ...
   *    val clock = T.start
   *    doSomeStuff()
   *    // ... and we're done
   *    clock()
   *    // alternately, `T.stop(clock)`
   *
   * Reusing a clock is not recommended; it will
   * record the time since the clock was first
   * created.
   */
  def start: () => Unit = {
    val stopwatch = startClock.run
    () => stopwatch.stop.run
  }

  /** Represents a running clock started at `startTime` */
  case class Clock(startTime: Long) {
    def stop: Task[Unit] = Task.delay(recordNanos(System.nanoTime - startTime))
  }

  /**
   * Returns a `Task` that constructs a running clock.
   * To record a time, call `stop` on the clock. Example:
   *
   *    val T: Timer = ...
   *    for {
   *      c <- T.startClock
   *      _ <- doSomeStuff
   *      // ... and we're done
   *      _ <- c.stop
   *    } yield ()
   *
   * A clock can be safely stopped multiple times;
   * stopping will record the time since the clock
   * was first created.
   */
  def startClock: Task[Clock] = Task.delay {
    Clock(System.nanoTime)
  }

  /** UNSAFE. A bit of syntax for stopping a stopwatch returned from `start`. */
  def stop(stopwatch: () => Unit): Unit = stopwatch()

  /**
   * UNSAFE. Evaluate `a` and record its evaluation time even if
   * evaluation completes with an error. Use `timeSuccess`
   * if you'd like to record a time only in the successful case.
   */
  def time[A](a: => A): A = measure(a).run

  /**
   * Evaluate `a` and record its evaluation time even if
   * evaluation completes with an error. Use `measureSuccess`
   * if you'd like to record a time only in the successful case.
   */
  def measure[A](a: => A): Task[A] = for {
    c <- startClock
    a <- Task.delay(a).onFinish(_ => c.stop)
  } yield a

  /**
   * UNSAFE. Like `time`, but records a time only if evaluation of
   * `a` completes without error.
   */
  def timeSuccess[A](a: => A): A = measureSuccess(a).run

  /**
   * Like `measure`, but records a time only if evaluation of
   * `a` completes without error.
   */
  def measureSuccess[A](a: => A): Task[A] = for {
    c <- startClock
    a <- Task.delay(a)
    _ <- c.stop
  } yield a

  /**
   * Time a `Future` by registering a callback on its
   * `onComplete` method. The stopwatch begins now.
   * This function records a time regardless if the `Future`
   * completes with an error or not. Use `timeFutureSuccess` or
   * explicit calls to `start` and `stop` if you'd like to
   * record a time only in the event the `Future` succeeds.
   */
  def timeFuture[A](f: Future[A])(implicit ctx: ExecutionContext = ExecutionContext.Implicits.global): Future[A] = {
    timeAsync(f.onComplete)
    f
  }

  /**
   * Like `timeFuture`, but records a time only if `f` completes
   * without an exception.
   */
  def timeFutureSuccess[A](f: Future[A])(implicit ctx: ExecutionContext = ExecutionContext.Implicits.global): Future[A] = {
    timeAsync((cb: A => Unit) => f.onSuccess({ case a => cb(a) }))
    f
  }

  /**
   * Time an asynchronous `Task`. The stopwatch begins running when
   * the returned `Task` is run and a stop time is recorded if the
   * `Task` completes in any state. Use `timeTaskSuccess` if you
   * wish to only record times when the `Task` succeeds.
   */
  def timeTask[A](a: Task[A]): Task[A] =
    Task.delay(start).flatMap { stopwatch =>
      a.attempt.flatMap { a =>
        stop(stopwatch)
        a.fold(Task.fail, Task.now)
      }
    }

  /**
   * Like `timeTask`, but records a time even if the `Task` completes
   * with an error.
   */
  def timeTaskSuccess[A](a: Task[A]): Task[A] =
    Task.delay(start).flatMap { stopwatch =>
      a.map { a => stop(stopwatch); a }
    }

  /**
   * Time a currently running asynchronous task. The
   * stopwatch begins now, and finishes when the
   * callback is invoked with the result.
   */
  def timeAsync[A](register: (A => Unit) => Unit): Unit = {
    val stopwatch = this.start
    register(_ => stop(stopwatch))
  }

  def measureAsync[A](register: (A => Unit) => Unit): Task[A] = for {
    c <- startClock
    a <- Task.async((k: (Throwable \/ A) => Unit) => register(a => k(right(a))))
    _ <- c.stop
  } yield a

  /**
   * Delay publishing updates to this `Timer` for the
   * given duration after modification. If multiple
   * timings are recorded within the window, only the
   * average of these timings is published.
   */
  def buffer(d: Duration)(
             implicit S: ScheduledExecutorService = Monitoring.schedulingPool,
             S2: ExecutorService = Monitoring.defaultPool): Timer[K] = {
    if (d < (100 microseconds))
      sys.error("buffer size be at least 100 microseconds, was: " + d)
    val nonce = new AtomicLong(0)
    val n = new AtomicInteger(0)
    val totalNanos = new AtomicLong(0)
    val scheduled = new AtomicBoolean(false)
    val later = Strategy.Executor(S2)
    new Timer[K] {
      def postNanos(delta: Long): Task[Unit] = for {
        _1 <- Task.delay(nonce.incrementAndGet)
        _2 <- Task.delay(totalNanos.addAndGet(delta))
        _3 <- Task.delay(nonce.incrementAndGet)
        _4 <- Task.delay(n.incrementAndGet)
        pp <- Task.delay(scheduled.compareAndSet(false, true))
        _  <- Task.schedule({
          scheduled.set(false)
          @annotation.tailrec
          def go: Unit = {
            val id = nonce.get
            val snapshotN = n.get
            val snapshotT = totalNanos.get
            if (nonce.get == id) { // we got a consistent snapshot
              val _1 = n.addAndGet(-snapshotN)
              val _2 = totalNanos.addAndGet(-snapshotT)
              val d = (snapshotT / snapshotN.toDouble).toLong
              // we don't want to hold up the scheduling thread,
              // as that could cause delays for other metrics,
              // so callback is run on `S2`
              val _ = later { self.recordNanos(d) }
              ()
            }
            else go
          }
          go
        }, d) whenM pp
      } yield ()
      def keys = self.keys
    }
  }
}
