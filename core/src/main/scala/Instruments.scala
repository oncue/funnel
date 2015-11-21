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

import java.util.concurrent.TimeUnit

import com.twitter.algebird.Group
import funnel.{Buffers => B}
import funnel.Buffers.TBuffer

import scala.concurrent.duration._
import scalaz.std.list._
import scalaz.std.tuple._
import scalaz.syntax.foldable._
import scalaz.syntax.functor._
import scalaz.stream.{Process,Process1}
import scalaz.concurrent.Task

import scalaz.concurrent.Task

/**
 * Provider of counters, gauges, and timers, tied to some
 * `Monitoring` instance. Instruments returned by this class
 * may update multiple metrics, see `counter`, `gauge` and
 * `timer` methods for more information.
 */
class Instruments(val monitoring: Monitoring = Monitoring.default,
                  val bufferTime: Duration = 200 milliseconds) {

  val window = monitoring.window

  private def nowL(s: String) = if (s == "") s else s"$s (now)"
  private def previousL(s: String) = if (s == "") s else s"$s ($window ago)"
  private def slidingL(s: String) = if (s == "") s else s"$s (past $window)"

  import monitoring.runLogging

  /**
    * takes a Key => Key and also sets the kind attribute
    */
  private def andKind[A](kind: String, keyMod: Key[A] => Key[A]): Key[A] => Key[A] =
    keyMod compose (k => k.copy(attributes = k.attributes + (AttributeKeys.kind -> kind)))

  /**
   * Return a `PeriodicGauge` with the given starting value.
   * Keys updated by this `PeriodicGauge` are `now/label`,
   * `previous/label` and `sliding/label`.
   * See [[funnel.Periodic]].
   */
  def periodicGauge[O:Reportable:Group](
    label: String, units: Units = Units.None, description: String = "", init: O,
    keyMod: Key[O] => Key[O] = identity[Key[O]] _): PeriodicGauge[O] = {
      val kinded = andKind("periodic", keyMod)
      val O = implicitly[Group[O]]
      val c = new PeriodicGauge[O] {
        val (ks, f) = nowPrevSliding(
          B.accum[O,O](init)(O.plus), identity[O],
          label, units, description, kinded).map(_.run)
        def append(n: O): Unit = {
          runLogging(f(n))
        }
        def keys = ks
      }
      c.buffer(bufferTime) // only publish updates this often
  }

  import scalaz.stream._

  def periodicKeys[O:Reportable](
    label: String,
    units: Units = Units.None,
    description: String,
    keyMod: Key[O] => Key[O] = identity[Key[O]] _) = {
      val trimmed = label.trim
      Periodic(Triple(("now", nowL _), ("previous", previousL _), ("sliding", slidingL _)).map {
        case (name, desc) => keyMod(Key[O](s"$name/$trimmed", units, desc(description)))
      })
  }

  def periodicBuffers[I,O:Group:Reportable](
    nowBuf: Process1[I,O],
    unit: I => O,
    label: String,
    units: Units = Units.None,
    description: String,
    keyMod: Key[O] => Key[O] = identity[Key[O]] _
  ): (Periodic[O], Triple[TBuffer[Option[I],O]]) = {
    val O = implicitly[Group[O]]
    val nowP = B.resetEvery(window)(nowBuf)
    val now = B.ignoreTick(nowP)
    val prev = B.emitEvery(window)(nowP)
    val sliding = B.ignoreTick(B.sliding(window)(unit)(O))
    (periodicKeys(label, units, description, keyMod), Triple(now, prev, sliding))
  }

  /**
   * Only for the advanced use case of creating new instrument types or complex composite metrics.
   * Creates three topics with names `prefix/label` where `prefix` is {`now`, `previous`, `sliding`}.
   * Publishes to `now` according to `nowBuf`, `prev` every `window`, and `sliding` continuously
   * while removing values when they are `window` old, according to the semantics of the `Group`.
   * Returns a composite `Periodic` key and a `Task` that publishes to all 3 topics.
   */
  def nowPrevSliding[I,O:Reportable:Group](
    nowBuf: Process1[I,O],
    unit: I => O,
    label: String,
    units: Units = Units.None,
    description: String,
    keyMod: Key[O] => Key[O] = identity[Key[O]] _
  ): (Periodic[O], Task[I => Task[Unit]]) = {
    import scalaz._
    import Scalaz._
    val (ks, nps) =
      periodicBuffers(nowBuf, unit, label, units, description, keyMod)
    val t = List(monitoring.topicWithKey(ks.now)(nps.one),
                 monitoring.topicWithKey(ks.previous, true)(nps.two),
                 monitoring.topicWithKey(ks.sliding)(nps.three)).traverse(
      _.map(Kleisli(_))).map(_.traverseU_(identity)).map(_.run)
    (ks, t)
  }

  /**
   * Return a `Counter` with the given starting count.
   * Keys updated by this `Counter` are `now/label`,
   * `previous/label` and `sliding/label`.
   * See [[funnel.Periodic]].
   * You should use counters only for metrics that are monotonic.
   */
  def counter(label: String,
              init: Int = 0,
              description: String = "",
              keyMod: Key[Double] => Key[Double] = identity): Counter =
    new Counter(periodicGauge[Double](
      label, Units.Count, description, init, andKind("counter", keyMod)))

  // todo: histogramgauge, histogramCount, histogramTimer
  // or maybe we just modify the existing combinators to
  // update some additional values

  /**
   * Records the elapsed time in the current period whenever the
   * returned `Gauge` is set. See `Elapsed.scala`.
   */
  private[funnel] def currentElapsed(label: String,
                                     desc: String,
                                     keyMod: Key[Double] => Key[Double] = identity): Gauge[Continuous[Double], Unit] = {
    val kinded = andKind("elapsed", keyMod)
    val (k, snk) = monitoring.topic[Unit,Double](label, Units.Seconds, desc, kinded)(
      B.currentElapsed(window).map(_.toSeconds.toDouble)).map(_.run)
    val g = new Gauge[Continuous[Double], Unit] {
      def set(u: Unit) = runLogging(snk(u))
      def keys = Continuous(k)
    }
    g.set(())
    g

  }

  /**
   * Records the elapsed time in the current period whenever the
   * returned `Gauge` is set. See `Elapsed.scala`.
   */
  private[funnel] def currentRemaining(label: String,
                                       desc: String): Gauge[Continuous[Double], Unit] = {
    val kinded = andKind("remaining", identity[Key[Double]])
    val (k, snk) = monitoring.topic[Unit,Double](label, Units.Seconds, desc, kinded)(
      B.currentRemaining(window).map(_.toSeconds.toDouble)).map(_.run)
    val g = new Gauge[Continuous[Double], Unit] {
      def set(u: Unit) = runLogging(snk(u))
      def keys = Continuous(k)
    }
    g.set(())
    g
  }

  /**
   * Records the elapsed time that the `Monitoring` instance has
   * been running whenver the returned `Gauge` is set. See `Elapsed.scala`.
   */
  private[funnel] def uptime(label: String): Gauge[Continuous[Double], Unit] = {
    val kinded = andKind("uptime", identity[Key[Double]])
    val (k, snk) = monitoring.topic[Unit,Double](label.trim,
                                                 Units.Minutes,
                                                 "Time elapsed since monitoring started",
                                                 kinded)(
      B.elapsed.map(_.toSeconds.toDouble / 60)).map(_.run)
    val g = new Gauge[Continuous[Double], Unit] {
      def set(u: Unit) = runLogging(snk(u))
      def keys = Continuous(k)
    }
    g.set(())
    g
  }

  /**
   * Return a `Gauge` with the given starting value.
   * This gauge only updates the key `now/\$label`.
   * For a historical gauge that summarizes an entire
   * window of values as well, see `numericGauge`.
   */
  def gauge[A:Reportable](label: String, init: A,
                          units: Units = Units.None,
                          description: String = "",
                          keyMod: Key[A] => Key[A] = identity[Key[A]] _): Gauge[Continuous[A],A] = {
    val kinded = andKind("gauge",keyMod)
    val g = new Gauge[Continuous[A],A] {
      val (key, snk) = monitoring.topic(s"now/${label.trim}", units, description, kinded)(
        B.ignoreTick(B.resetEvery(window)(B.variable(init)))).map(_.run)
      def set(a: A) = runLogging(snk(_ => a))
      def keys = Continuous(key)

      set(init)
    }
    g.buffer(bufferTime)
  }

  /**
   * Return an `Edge` with the given initial `origin` and `destination`.
   * See [[Edge]] for more information.
   *
   * This will create the following instruments and keys:
   *
   * A string gauge  `now/$$label/origin`.
   * A string gauge  `now/$$label/destination`.
   * A timer         `?/$$label/timer` where `?` is `now`, `previous`, and `sliding`.
   * A traffic light `now/$$label/status`
   *
   * @param label The name of the traffic light metric
   * @param description A human-readable descirption of the semantics of this metric
   * @param origin The source of the request. Typically this should be the IP address of the calling host
   * @param destination The target of the outbound request; typically this is IP or DNS.
   */
  def edge(
    label: String,
    description: String = "",
    origin: Edge.Origin,
    destination: Edge.Destination): Edge = {
      val trimmed = label.trim
      def addEdge[A](k: Key[A]): Key[A] = k.setAttribute(AttributeKeys.edge, trimmed)
      Edge(
        origin = gauge(
          label  = s"$trimmed/origin",
          init   = origin,
          keyMod = addEdge[Edge.Origin]),
        destination = gauge(
          label  = s"$trimmed/destination",
          init   = destination,
          keyMod = addEdge[Edge.Destination]),
        timer = timer( // should update to lapTimer, but won't be binary compatible
          label  = s"$trimmed/latency",
          keyMod = addEdge),
        status = trafficLight(
          label  = s"$trimmed/status",
          keyMod = addEdge)
      )
  }

  /**
   * Return a `TrafficLight`--a gauge whose value can be `Red`, `Amber`,
   * or `Green`. The initial value is `Green`. The key is `now/$$label`.
   *
   * @param label The name of the traffic light metric
   * @param description A human-readable descirption of the semantics of this metric
   */
  def trafficLight(label: String,
                   description: String = "",
                   keyMod: Key[String] => Key[String] = identity[Key[String]]): TrafficLight =
    TrafficLight(gauge(label, TrafficLight.Green, Units.TrafficLight, description, andKind("traffic", keyMod)))

  /**
   * Return a `Gauge` with the given starting value.
   * Unlike `gauge`, keys updated by this `Counter` are
   * `now/label`, `previous/label` and `sliding/label`.
   * See [[funnel.Periodic]].
   */
  def numericGauge(label: String, init: Double,
                   units: Units = Units.None,
                   description: String = "",
                   keyMod: Key[Stats] => Key[Stats] = identity): Gauge[Periodic[Stats],Double] = {
    val kinded = andKind("numeric", keyMod)
    val g = new Gauge[Periodic[Stats],Double] {
      val (ks, f) =
        nowPrevSliding(B.stats, Stats(_:Double), label, units, description, kinded).map(_.run)
      def keys = ks
      def set(d: Double): Unit = runLogging(f(d))
      set(init)
    }
    g.buffer(bufferTime)
  }

  /**
   * Return a `Timer` which updates the following keys:
   * `now/label`, `previous/label`, and `sliding/label`.
   * See [[funnel.Periodic]].
   */
  def timer(label: String,
            description: String = "",
            keyMod: Key[Stats] => Key[Stats] = identity): Timer[Periodic[Stats]] = {
    val kinded = andKind("timer", keyMod)
    val t = new Timer[Periodic[Stats]] {
      val u: Units = Units.Duration(TimeUnit.MILLISECONDS)
      val (ks, f) =
        nowPrevSliding(B.stats, Stats(_:Double), label, u, description, kinded).map(_.run)
      def keys = ks
      def recordNanos(nanos: Long): Unit = {
        // record time in milliseconds
        val millis = nanos.toDouble / 1e6
        runLogging(f(millis))
      }
    }
    t.buffer(bufferTime)
  }

  /**
   * Return a `LapTimer` which updates the following keys:
   * `now/label`, `previous/label`, and `sliding/label`.
   * See [[funnel.LapTimer]].
   *
   * @param label The name of the lap timer metric
   * @param description A human-readable description of the semantics of this metric
   */
  def lapTimer(label: String, description: String = ""): LapTimer = {
    val trimmed = label.trim
    def setAttribute[A](k: Key[A]): Key[A] = k.setAttribute("laptimer", label)
    new LapTimer(
      timer = timer(
        label  = s"$trimmed",
        keyMod = setAttribute),
      counter = counter(
        label  = s"$trimmed",
        keyMod = setAttribute)
    )
  }

}
