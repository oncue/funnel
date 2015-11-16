package funnel

import com.twitter.algebird.Group
import java.util.concurrent.{ExecutorService, ScheduledExecutorService}
import java.util.concurrent.TimeUnit
import scala.concurrent.duration._
import scalaz.concurrent.{Task, Strategy}

/**
 * A gauge whose readout value type is characterized by a `Group`.
 *
 * See http://en.wikipedia.org/wiki/Group_%28mathematics%29
 */
abstract class PeriodicGauge[A](implicit A: Group[A]) extends Instrument[Periodic[A]] { self =>

  /** UNSAFE. Add the given value to the current value of the gauge. */
  def append(a: A): Unit = add(a).run

  /** UNSAFE. Subtract the given value from the current value of the gauge. */
  final def remove(a: A): Unit =
    append(A.negate(a))

  /** Add the given value to the current value of the gauge. */
  def add(a: A): Task[Unit]

  /** Subtract the given value from the current value of the gauge. */
  final def subtract(a: A): Task[Unit] =
    add(A.negate(a))

  /**
   * Delay publishing updates to this `GroupGauge` for the
   * given duration after modification.
   */
  def buffer(d: Duration)(
             implicit S: ScheduledExecutorService = Monitoring.schedulingPool,
             S2: ExecutorService = Monitoring.defaultPool): PeriodicGauge[A] =
    new PeriodicGauge[A] {
      val b = new Gauge.Buffer(d, A.zero)(A.plus, _ => A.zero, self.append)
      def add(a: A) = b(a)
      def keys = self.keys
    }
}

