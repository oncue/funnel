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

import com.twitter.algebird.Group
import org.scalacheck.Arbitrary._
import org.scalacheck.Prop._
import org.scalacheck._

import scala.concurrent.duration._
import scalaz.Nondeterminism
import scalaz.concurrent.Task
import scalaz.std.list._
import scalaz.std.tuple._
import scalaz.stream.Process
import scalaz.syntax.foldable._
import scalaz.syntax.functor._

object MonitoringSpec extends Properties("monitoring") {

  val B = Buffers

  // we are dealing with doubles, so don't want to rely on exact comparison,
  // should be within a small epsilon though
  val Epsilon = 1e-7

  // some syntax for === and !== for epsion comparisons
  implicit class DoubleSyntax(d: Double) {
    def ===[N](n: N)(implicit N: Numeric[N]) = (d - N.toDouble(n)).abs < Epsilon
    def !==[N](n: N)(implicit N: Numeric[N]) = (d - N.toDouble(n)).abs >= Epsilon
  }
  implicit class DoubleSeqSyntax(ds: Seq[Double]) {
    def ===[N](ns: Seq[N])(implicit N: Numeric[N]) =
      ns.length == ds.length && ds.zip(ns.map(N.toDouble)).forall { case (a,b) => a === b }
  }

  // +/- 200 trillion
  implicit val arbLong: Arbitrary[Long] = Arbitrary(Gen.choose(-200000000000000L, 200000000000000L))
  /*
   * Check that `roundDuration` works as expected for
   * some hardcoded examples.
   */
  property("roundDuration") = secure {
    B.ceilingDuration(0.minutes, 5.minutes) == (5.minutes) &&
    B.ceilingDuration(14.seconds, 1.minutes) == (1.minutes) &&
    B.ceilingDuration(60.seconds, 1.minutes) == (2.minutes) &&
    B.ceilingDuration(61.seconds, 1.minutes) == (2.minutes) &&
    B.ceilingDuration(59.seconds, 2.minutes) == (2.minutes) &&
    B.ceilingDuration(119.seconds, 1.minutes) == (2.minutes) &&
    B.ceilingDuration(120.seconds, 1.minutes) == (3.minutes) &&
    B.ceilingDuration(190.milliseconds, 50.milliseconds) == (200.milliseconds)
  }

  /*
   * Check that `counter` properly counts.
   */
  property("counter") = forAll { (xs: List[Long]) =>
    val c = B.counter(0)
    val input: Process[Task,Long] = Process.emitAll(xs)
    val out = input.pipe(c).runLog.run
    out == xs.scanLeft(0.0)(_ + _)
  }

  /*
   * Check that `resetEvery` properly resets the stream
   * transducer after the elapsed time. Check that `emitEvery`
   * only emits a value at period boundaries.
   * Also check that `emitEvery` emits when it sees two `None` values.
   */
  property("reset/emitEvery") = forAll { (h: Long, t: List[Long]) =>
    val xs = h :: t
    // resetEvery -- we feed the same input twice, fast forwarding
    // the time; this should give the same output, duplicated
    val c = B.resetEvery(5.minutes)(B.counter(0))
    val input: Process[Task,(Long,Duration)] =
      Process.emitAll(xs.map((_, 0.minutes))) ++
      Process.emitAll(xs.map((_, 5.minutes)))
    val input3: Process[Task,(Option[Long],Duration)] =
      Process.emitAll(xs.map(x => (Some(x), 0.minutes))) ++
      Process.emitAll(xs.map(x => (Some(x), 1.minutes)))
    val out = input.pipe(c).runLog.run
    require(out.length % 2 == 0, "length of output should be even")
    val (now, later) = out.splitAt(out.length / 2)
    val ok = (now === later) && (now === xs.scanLeft(0.0)(_ + _))

    // emitEvery -- we should only emit two values, one at the
    // end of the first period, and one at the end of the second
    val c2 = B.emitEvery(5.minutes)(c)
    val input2 = input ++ Process(1L -> (11.minutes))
    val out2 = input2.map{ case (x, y) => (Some(x), y) }.pipe(c2).runLog.run

    val out3 = (input3 ++ Process(None -> 2.minutes, None -> 11.minutes)).pipe(c2).runLog.run

    ok &&
    out2.length === 2 &&
    out2(0) === xs.sum &&
    out2(1) === xs.sum &&
    out3(0) === xs.sum * 2
  }


  /* Check that if all events occur at same moment, `sliding` has no effect. */
  property("sliding-id") = forAll(Gen.nonEmptyListOf(Gen.choose(1,10))) { xs =>
    val c = B.sliding(5.minutes)(identity[Int])(Group.intGroup)
    val input: Process[Task,(Int,Duration)] =
      Process.emitAll(xs.map((_, 1.minutes)))
    val output = input.pipe(c).runLog.run
    output == xs.scanLeft(0)(_ + _)
  }

  /* Example of sliding count. */
  property("sliding-example") = secure {
    val i1: Process[Task, (Int,Duration)] =
      Process(1 -> (0.minutes), 1 -> (1.minutes), 2 -> (3.minutes), 2 -> (4.minutes))

    val c = B.sliding(2.minutes)(identity[Int])(Group.intGroup)
    val output = i1.pipe(c).runLog.run
    output == List(0, 1, 2, 3, 4)
  }

  /*
   * Check that all values are eventually received by a
   * buffered signal.
   */
  property("bufferedSignal") = forAll { (xs: List[Long]) =>
    val (snk, s) = Monitoring.bufferedSignal(B.counter(0)).run
    xs.traverse_(snk).run
    val expected = xs.sum
    // this will 'eventually' become true, and loop otherwise
    while (s.continuous.once.runLastOr(0.0).run !== expected) {
      Thread.sleep(10)
    }
    true
  }


  /*
   * Check that subscribing and filtering is the same as
   * filtering and subscribing.
   */
  property("subscribe") = secure {
    val M = Monitoring.instance(windowSize = 6.seconds)
    implicit val log = (_:String) => ()
    def listenFor[A](t: Duration)(p: Process[Task, A]): Vector[A] = {
      val b = new java.util.concurrent.atomic.AtomicBoolean(false)
      var v = Vector[A]()
      p.evalMap(a => Task {
        v = v :+ a
      }).run.runAsyncInterruptibly(_ => (), b)
      Thread.sleep(t.toMillis)
      b.set(true)
      v
    }
    new Instruments(M) {
      JVM.instrument(this)
    }
    val b1 = Monitoring.subscribe(M)(_ => true).
      filter(_.key.name.contains("previous/jvm/gc/ParNew/time"))
    val b2 = Monitoring.subscribe(M)(
      _.name.contains("previous/jvm/gc/ParNew/time"))
    val xs = listenFor(30.seconds)(b1)
    val ys = listenFor(30.seconds)(b2)
    val d = (xs.length - ys.length).abs
    d <= 2 // Each of xs and ys could gain or lose one tick, for a total of 2
  }

  /* Check that `distinct` combinator works. */
  property("distinct") = forAll(Gen.nonEmptyListOf(Gen.choose(-10L,10L))) { xs =>
    val input: Process[Task,Long] = Process.emitAll(xs)
    input.pipe(B.distinct).runLog.run.toList == xs.distinct
  }

  /* Check that publishing to a bufferedSignal is 'fast'. */
  property("bufferedSignal-profiling") = secure {
    def go: Boolean = {
      val N = 100000
      val (snk, s) = Monitoring.bufferedSignal(B.counter(0)).run
      val t0 = System.nanoTime
      (0 to N).toList.traverse_(x => snk(x)).run
      val expected = (0 to N).map(_.toDouble).sum
      while (s.continuous.once.runLastOr(0.0).run !== expected) {
        Thread.sleep(10)
      }
      val d = Duration.fromNanos(System.nanoTime - t0) / N.toDouble
      // println("Number of microseconds per event: " + d.toMicros)
      // I am seeing around 25 microseconds on avg
      d.toMicros < 1000
    }
    go || go || go // decrease false negative rate by retrying three times
  }

  /*
   * Counter and Gauge updates should be 'fast', and should work
   * with concurrent producers.
   */
  property("profiling") = secure {
    def go: Prop = {
      import instruments._
      val c = counter("uno")
      val ok = gauge("tres", false)
      val N = 1000000
      val t0 = System.nanoTime
      val S = scalaz.concurrent.Strategy.DefaultStrategy
      val f1 = S { (0 until N).foreach { _ =>
        c.increment
        ok.set(true)
      }}
      val f2 = S { (0 until N).foreach { _ =>
        c.increment
        ok.set(true)
      }}
      f1(); f2()
      val updateTime = Duration.fromNanos(System.nanoTime - t0) / N.toDouble
      val get: Task[Double] = Monitoring.default.latest(c.keys.now)
      while (get.run != N*2) {
        // println("current count: " + get.run)
        Thread.sleep(10)
      }
      val publishTime = Duration.fromNanos(System.nanoTime - t0) / N.toDouble
      val okResult = Monitoring.default.latest(ok.keys.now).run
      Thread.sleep(instruments.bufferTime.toMillis * 2)

      //println("update time: " + updateTime)
      //println("publishTime: " + publishTime)
      //println("OK result:" + okResult)
      // I am seeing about 40.nanoseconds for update times,
      // 100 nanos for publishing
      (s"Gauge latency should be < 1 μs (was $updateTime)" |: (updateTime.toNanos < 1000)) &&
      (s"Publish latency should be < 2 μs (was $publishTime)" |: (publishTime.toNanos < 2000)) &&
      okResult
    }
    go || go || go
  }

  /* Make sure key senesence doesn't have quadratic complexity */
  property("key-senesence") = secure {
    def go: Boolean = {
      val ranges = List(4, 8, 16, 32, 64, 128).map(Range(0, _))
      val times = ranges map { r =>
        val M = Monitoring.instance(windowSize = 30.seconds)
        val I = new Instruments(M)
        import I._
        val counters = r.toList.map(n => counter(s"test$n"))
        val t0 = System.nanoTime
        val ks = M.keys.discrete.dropWhile(_.isEmpty).evalMap(x =>
          if (x.isEmpty)
            M.keys.close
          else
            Task.now(())
        )
        M.keySenescence(Events.every(100.milliseconds), M.distinctKeys).zip(ks).run.run
        val t = System.nanoTime - t0
        t
      }
      times.zip(times.tail).foldLeft(true) {
        case (b, (t1, t2)) =>
          val dt = t2.toDouble / t1.toDouble
          // Doubling input size should have complexity closer to 2x than 4x
          b && (Math.abs(2-dt) < Math.abs(4-dt))
      }
    }
    go || go || go
  }

  /* Simple sanity check of a timer. */
  property("timer-ex") = secure {
    def go: Boolean = {
      import instruments._
      val t = timer("uno")
      t.time { Thread.sleep(50) }
      // Make sure we wait for the time buffer to catch up
      Thread.sleep(instruments.bufferTime.toMillis * 2)
      val r = Monitoring.default.latest(t.keys.now).run.mean
      //println("Sleeping for 50ms took: " + r)
      r > 0 && (r - 50).abs < 1000
    }
    go || go || go
  }

  /* Make sure timer updates are 'fast'. */
  property("timer-profiling") = secure {
    def go: Boolean = {
      import instruments._
      val t = timer("uno")
      val N = 1000000
      val t0 = System.nanoTime
      val d = (50.milliseconds)
      (0 until N).foreach { _ =>
        t.record(d)
      }
      val delta = System.nanoTime - t0
      val updateTime = (delta.nanoseconds) / N.toDouble
      // Make sure we wait for the time buffer to catch up
      Thread.sleep(instruments.bufferTime.toMillis * 2)
      val m = Monitoring.default.latest(t.keys.now).run.mean
      //println("timer:updateTime: " + updateTime + ", m: " + m)
      updateTime.toNanos < 1000 && m == 50
    }
    go || go || go
  }

  /* Make sure timers allow concurrent updates. */
  property("concurrent-timing") = secure {
    def go: Boolean = {
      import instruments._
      val t = timer("uno")
      val N = 100000
      val S = scalaz.concurrent.Strategy.DefaultStrategy
      val t0 = System.nanoTime
      val d1 = (1.milliseconds); val d2 = (3.milliseconds)
      val f1 = S { (0 until N).foreach { _ =>
        t.record(d1)
      }}
      val f2 = S { (0 until N).foreach { _ =>
        t.record(d2)
      }}
      f1(); f2()
      val updateTime = Duration.fromNanos(System.nanoTime - t0) / N.toDouble
      Thread.sleep(200)
      // average time should be 2 millis
      val m = Monitoring.default.latest(t.keys.now).run.mean
      // println("average time: " + m)
      // println("timer:updateTime: " + updateTime)
      m === 2.0 && updateTime.toNanos < 1000
    }
    go || go || go
  }

  /** Check that when publishing, we get the count that was published. */
  property("pub/sub") = forAll(Gen.nonEmptyListOf(Gen.choose(1,10))) { a =>
    val M = Monitoring.default
    val (k, snk) = M.topic[Long,Double]("count", Units.Count, "")(B.ignoreTickAndTime(B.counter(0))).map(_.run)
    val count = M.get(k)
    a.traverse_(x => snk(x)).run
    val expected = a.sum
    var got = count.continuous.once.runLastOr(0.0).run
    while (got !== expected) {
      got = count.continuous.once.runLastOr(0.0).run
      Thread.sleep(10)
    }
    true
  }

  /*
   * Feed a counter concurrently from two different threads, making sure
   * the final count is the same as if we summed sequentially.
   */
  property("concurrent-counters-integration-test") = forAll(Gen.nonEmptyListOf(Gen.choose(-10,10))) { ab =>
    // this test takes about 45 seconds
    val (a,b) = ab.splitAt(ab.length / 2)
    val M = Monitoring.instance(windowSize = 30.seconds)
    val I = new Instruments(M)
    import I._
    val aN = counter("a")
    val bN = counter("b")
    val abN = counter("ab")
    val latest = Monitoring.snapshot(M)
    Nondeterminism[Task].both(
      Task { a.foreach { a => aN.incrementBy(a); abN.incrementBy(a) } },
      Task { b.foreach { b => bN.incrementBy(b); abN.incrementBy(b) } }
    ).run
    val expectedA: Double = a.map(_.toDouble).sum
    val expectedB: Double = b.map(_.toDouble).sum
    val expectedAB: Double = ab.map(_.toDouble).sum
    @annotation.tailrec
    def go(): Unit = {
      val gotA: Double = M.latest(aN.keys.now).run
      val gotB: Double = M.latest(bN.keys.now).run
      val gotAB: Double = M.latest(abN.keys.now).run
      if ((gotA !== expectedA) || (gotB !== expectedB) || (gotAB !== expectedAB)) {
        // println("sleeping")
        // println(s"a: $gotA, b: $gotB, ab: $gotAB")
        Thread.sleep(10)
        go()
      }
    }
    go()
    val t0 = System.currentTimeMillis
    val m = latest.run
    val millis = System.currentTimeMillis - t0
    // println(s"snapshot took: $millis")
    (m(aN.keys.now).value.asInstanceOf[Double] === expectedA) &&
    (m(bN.keys.now).value.asInstanceOf[Double] === expectedB) &&
    (m(abN.keys.now).value.asInstanceOf[Double] === expectedAB)
  }

  // This takes too long to run.
  // Commenting out for now. -- Runar
  /* property("derived-metrics") = forAll(Gen.nonEmptyListOf(Gen.choose(-10,10))) { ls0 =>
    val ls = ls0.take(50)
    implicit val M = Monitoring.instance
    val I = new Instruments(5.minutes, M); import I._
    val a = counter("a")
    val b = counter("b")

    val ab = Metric.apply2(a.key, b.key)(_ + _)
    val kab1 = ab.publishEvery(30.milliseconds)("sum:ab-1", Units.Count)
    val kab2 = ab.publishOnChange(a.key)("sum:ab-2", Units.Count)
    val kab3 = ab.publishOnChanges(a.key, b.key)("sum:ab-3", Units.Count)

    Strategy.Executor(Monitoring.defaultPool) {
      ls.foreach(a.incrementBy)
    }
    Strategy.Executor(Monitoring.defaultPool) {
      ls.foreach(b.incrementBy)
    }

    val expected = ls.map(_.toDouble).sum * 2

    def go(rounds: Int): Prop = {
      Thread.sleep(30)
      val ab1r = M.latest(kab1).run
      val ab2r = M.latest(kab2).run
      val ab3r = M.latest(kab3).run
      // since ab2r is only refreshed when `a` changes, we
      // artifically refresh `a`, otherwise this test would
      // have a race condition if `a` completed before `b`
      if (ab2r != expected) a.incrementBy(0)
      // println((ab1r, ab2r, ab3r))
      (ab1r === ab2r) && (ab2r === ab3r) && (ab3r === expected) || {
        if (rounds == 0) "results: " + (ab1r, ab2r, ab3r).toString |: false
        else go(rounds - 1)
      }
    }
    go(15)
  }*/

  // Commenting out since I don't know what this is testing
  // and it doesn't seem to work. -- Runar
  /*property("aggregate") = secure {
    List(List(), List(1), List(-1,1), List.range(0,100)).forall { xs =>
      val M = Monitoring.instance
      val I = new Instruments(5.minutes, M)
      val counters = xs.zipWithIndex.map { case (x,i) =>
        val c = I.counter(s"count/$i")
        c.incrementBy(x)
        c
      }
      val family = Key[Double]("now/count", Units.Count)
      val out = Key[Double]("sum", Units.Count)
      M.aggregate(family, out)(Events.takeEvery(15.milliseconds, 50))(_.sum).run
      Thread.sleep(1000)
      // println("xs: " + xs)
      val l = M.latest(out).run
      val r = xs.map(_.toDouble).sum
      l === r || { println((l, r)); false }
    }
  }*/

  property("TrafficLight.quorum") = secure {
    import TrafficLight._
    quorum(1)(List()) == Red &&
    quorum(1)(List(Red)) == Red &&
    quorum(1)(List(Green)) == Green &&
    quorum(1)(List(Amber)) == Green &&
    quorum(1)(List(Red,Green)) == Amber &&
    quorum(1)(List(Amber,Green)) == Green &&
    quorum(1)(List(Green,Green)) == Green &&
    quorum(2)(List(Green,Green,Green)) == Green &&
    quorum(2)(List(Green,Amber,Green)) == Green &&
    quorum(2)(List(Red,Amber,Green)) == Amber &&
    quorum(2)(List(Red,Red,Green)) == Red
  }

  property("TrafficLight.fraction") = secure {
    import TrafficLight._
    fraction(.5)(List()) == Green &&
    fraction(.5)(List(Red)) == Red &&
    fraction(.5)(List(Green)) == Green &&
    fraction(.5)(List(Amber)) == Green &&
    fraction(.5)(List(Red,Green)) == Amber &&
    fraction(.5)(List(Amber,Green)) == Green &&
    fraction(.5)(List(Green,Green)) == Green &&
    fraction(.67)(List(Green,Green,Green)) == Green &&
    fraction(.67)(List(Green,Amber,Green)) == Green && true
    fraction(.66)(List(Red,Amber,Green)) == Amber &&
    fraction(.68)(List(Red,Amber,Green)) == Red &&
    fraction(.66)(List(Red,Red,Green)) == Red &&
    fraction(.5)(List(Red,Red,Green,Green)) == Amber
  }

  /* Simple sanity check of LapTimer. */
  property("LapTimer.basic") = secure {
    def go: Boolean = {
      import instruments._

      import scala.concurrent.ExecutionContext.Implicits.global
      import scala.concurrent._
      import scala.language.postfixOps
      val label = "laptimer"
      val t = timer(label)
      val c = counter(label)
      val lt = new LapTimer(t, c)
      lt.record(50 milliseconds)
      lt.recordNanos(50000)
      val stop = lt.start
      Thread.sleep(50)
      lt.stop(stop)
      lt.time { Thread.sleep(50) }
      lt.timeFuture(Future { Thread.sleep(50); None })
      lt.timeTask(Task { Thread.sleep(50); None }).run
      // Make sure we wait for the time buffer to catch up
      Thread.sleep(instruments.bufferTime.toMillis * 2)
      val m = Monitoring.default
      val r1 = m.latest(t.keys.now).run.mean
      val r2 = m.latest((c.keys.now)).run
      r1 > 0 && (r1 - 50).abs < 1000 && r2 == 6
    }
    go || go || go
  }

  /* Make sure timers allow concurrent updates. */
  property("LapTimer.concurrent") = secure {
    def go: Boolean = {
      import instruments._
      val label = "laptimer"
      val t = timer(label)
      val c = counter(label)
      val lt = new LapTimer(t, c)
      val N = 100000
      val S = scalaz.concurrent.Strategy.DefaultStrategy
      val t0 = System.nanoTime
      val d1 = (1.milliseconds); val d2 = (3.milliseconds)
      val f1 = S { (0 until N).foreach { _ =>
        lt.record(d1)
      }}
      val f2 = S { (0 until N).foreach { _ =>
        lt.record(d2)
      }}
      f1(); f2()
      val updateTime = Duration.fromNanos(System.nanoTime - t0) / N.toDouble
      Thread.sleep(200)
      // average time should be 2 millis
      val m = Monitoring.default
      val r1 = m.latest(t.keys.now).run.mean
      val r2 = m.latest((c.keys.now)).run
      r1 === 2.0 && updateTime.toNanos < 1000 && r2 == N*2
    }
    go || go || go
  }

}
