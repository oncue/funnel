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

import scala.concurrent.duration._
import scalaz.stream._
import scalaz.stream.{Process => P}
import scalaz.Monoid
import com.twitter.algebird.Group

/**
 * Various stream transducers and combinators used for
 * building arguments to pass to `Monitoring.topic`.
 * See `Monitoring` companion object for examples of how
 * these can be used.
 */
object Buffers {

  type Buffer[T,I,O] = Process1[(I,T),O]
  type TBuffer[I,O] = Buffer[Duration,I,O]

  /** Promote a `Process1[A,B]` to one that ignores time. */
  def ignoreTime[A,B](p: Process1[A,B]): Buffer[Any,A,B] =
    process1.id[(A,Any)].map(_._1).pipe(p)

  def ignoreTickAndTime[A,B](p: Process1[A,B]): TBuffer[Option[A],B] =
    ignoreTick(ignoreTime(p))

  /**
   * Promote a buffer to one that ignores empty inputs.
   * We use empty inputs as a signal that the buffer should be flushed.
   */
  def ignoreTick[A,B](p: TBuffer[A,B]): TBuffer[Option[A],B] =
    process1.id[(Option[A],Duration)].flatMap {
      case (Some(a), t) => Process.emit(a -> t)
      case _ => Process.halt
    } pipe p

  /** Emit the input duration. */
  def elapsed: TBuffer[Any,Duration] =
    process1.id[(Any,Duration)].map(_._2)

  /**
   * Emit the elapsed time in the current period, where periods are
   * of `step` duration.
   */
  def currentElapsed(step: Duration): TBuffer[Any,Duration] =
    process1.id[(Any,Duration)].map { case (_, d) =>
      val d0 = floorDuration(d, step)
      d - d0
    }

  /**
   * Emit the remaining time in the current period, where periods are
   * of `step` duration.
   */
  def currentRemaining(step: Duration): TBuffer[Any,Duration] =
    process1.lift { (p: (Any,Duration)) =>
      val d = p._2
      val d1 = ceilingDuration(d, step)
      d1 - d
    }

  /**
   * Emits the current value, which may be modified by the
   * given function, which receives the old value and
   * produces the new value.
   */
  def variable[A](init: A): Process1[A => A, A] =
    P.emit(init) ++ P.await1[A => A].flatMap(f => variable(f(init)))

  /** Emits a running sum of its inputs, starting from `init`. */
  def counter(init: Long): Process1[Long,Double] =
    process1.scan(init.toDouble)(_ + _)

  /**
   * Emits a running sum of its inputs, starting from the `zero`
   * value of the given `Monoid`.
   */
  def sum[A](implicit A: Monoid[A]): Process1[A,A] =
    accum(A.zero)(A.append(_, _))

  /**
   * Emits a running accumulation of its inputs, starting from `z`,
   * and appending new values with `f`.
   */
  def accum[A,B](z: B)(f: (B, A) => B): Process1[A, B] =
    process1.scan(z)(f)

  /** Emits only values not yet seen. */
  def distinct[A]: Process1[A, A] = {
    def go(seen: Set[A]): Process1[A, A] =
      P.await1[A].flatMap { a =>
        if (seen(a)) go(seen)
        else P.emit(a) ++ go(seen + a)
      }
    go(Set.empty)
  }

  /** Reset the buffer `p` after `d0, 2*d0, 3*d0, ...` elapsed duration. */
  def resetEvery[I,O](d0: Duration)(p: Process1[I,O]): TBuffer[I,O] = {
    def flush(cur: Process1[I,O], expiration: Duration):
        Process1[(I,Duration),O] = {
      val (h, t) = cur.unemit
      Process.emitAll(h) ++ go(t, expiration)
    }
    def go(cur: Process1[I,O], expiration: Duration): TBuffer[I,O] =
      P.await1[(I,Duration)].flatMap { case (i,d) =>
        if (d >= expiration)
          flush(process1.feed1(i)(p), ceilingDuration(d, d0))
        else
          flush(process1.feed1(i)(cur), expiration)
      }
    flush(p, d0)
  }

  /**
   * Only emit a value after `d0, 2*d0, 3*d0, ...` elapsed duration.
   * Takes `Option[I]` to allow for the clock to tick independently of the inputs,
   * so we can flush the buffer on a schedule if there is no input for a while.
   */
  def emitEvery[I,O](d0: Duration)(p: TBuffer[I,O]): TBuffer[Option[I],O] = {
    def flush(last: Option[O], unmask: Boolean, cur: TBuffer[I,O], expiration: Duration):
        TBuffer[Option[I],O] = {
      val (h, t) = cur.unemit
      val last2 = h.lastOption
      if (unmask) Process.emitAll(last.toSeq) ++ go(last2, t, expiration)
      else go(last2 orElse last, t, expiration)
    }
    def go(last: Option[O], cur: TBuffer[I,O], expiration: Duration): TBuffer[Option[I],O] =
      P.await1[(Option[I],Duration)].flatMap {
        case (x, d) =>
          if (d >= expiration)
            flush(last, true,
                  x.map(i => process1.feed1(i -> d)(p)).getOrElse(cur),
                  ceilingDuration(d, d0))
          else
            flush(last, false, x.map(i => process1.feed1(i -> d)(cur)).getOrElse(cur), expiration)
      }
    flush(None, false, p, d0)
  }

  /** Produce a sliding window output from all inputs in the last `d0` duration. */
  def sliding[I,O](d0: Duration)(to: I => O)(G: Group[O]): TBuffer[I,O] = {
    def go(window: Vector[(O, Duration)], cur: O): TBuffer[I,O] =
      P.await1[(I,Duration)].flatMap { case (i,d) =>
        val (out, in) = window.span(d - _._2 > d0)
        val io = to(i)
        val neg = out.foldLeft(G.zero)((a,b) => G.plus(a, G.negate(b._1)))
        val cur2 = G.plus(G.plus(neg, cur), io)
        P.emit(cur2) ++ go(in :+ (io -> d), cur2)
      }
    P.emit(G.zero) ++ go(Vector(), G.zero)
  }

  /** Compute the smallest multiple of `step` which is `> d`. */
  def ceilingDuration(d: Duration, step: Duration): Duration = {
    val f = d / step
    val d2: Duration = step * math.ceil(f)
    if (math.ceil(f) == f) d2 + step
    else d2
  }

  /** Compute the smallest multiple of `step` which is `<= d` `*/
  def floorDuration(d: Duration, step: Duration): Duration =
    ceilingDuration(d, step) - step

  def resettingRate(d: Duration): TBuffer[Long,Double] =
    resetEvery(d)(counter(0))

  def stats: Process1[Double, Stats] =
    process1.id[Double].map(Stats.apply).scan1(_ ++ _)

  def histogram[O <% Reportable[O]]: Process1[String,Histogram[O]] = ???

  /**
   * Approximate count of unique `A` values using HyperLogLog algorithm.
   * This uses constant space.
   */
  def hyperLogLog[A]: Process1[A,Int] = ???

  // def countMinSketch, etc

  def incr: Int => Int = _ + 1
  def decr: Int => Int = _ - 1
  def incrBy(n: Int): Int => Int = _ + n
  def decrBy(n: Int): Int => Int = _ - n
}
