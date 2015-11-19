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

/**
 * A key set for a periodic metric. Periodic metrics
 * have some window size (say 5 minutes).
 *
 * `now` can be used to lookup the most recent value,
 * `previous` can be used to lookup the most recent
 * value from a full window (for instance, the value
 * for the previous complete 5 minute period), and
 * `sliding` can be used to lookup the value summarizing
 * the last 5 minutes (or whatever the window size)
 * on a rolling basis.
 */
case class Periodic[+A](now: Key[A], previous: Key[A], sliding: Key[A]) {
  def toTriple: Triple[Key[A]] = Triple(now, previous, sliding)
  def |@|[AA >: A, B](m: Metric[B]) = {
    import scalaz.syntax.applicative._
    Metric.periodicToMetric[AA](this) |@| m
  }
}

object Periodic {
  def apply[A](keys: Triple[Key[A]]): Periodic[A] = Periodic(keys.one, keys.two, keys.three)
}

/**
 * A key set for a metric defined only in the present.
 * This would be used for a type like `String` for
 * which there isn't a good way of aggregating or
 * summarizing multiple historical values.
 */
case class Continuous[+A](now: Key[A]) {
  def |@|[AA >: A, B](m: Metric[B]) = {
    import scalaz.syntax.applicative._
    Metric.continuousToMetric[AA](this) |@| m
  }
}

