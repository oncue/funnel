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

case class TrafficLight(gauge: ContinuousGauge[TrafficLight.State]){
  import TrafficLight._
  def red = gauge.set(Red)
  def green = gauge.set(Green)
  def amber = gauge.set(Amber)
  // for the americans
  def yellow = gauge.set(Amber)
  def key = gauge.key
}
object TrafficLight {
  type State = String

  private[funnel] val Red = "red"
  private[funnel] val Amber = "amber"
  private[funnel] val Yellow = Amber
  private[funnel] val Green = "green"

  private def downgrade(anyFailures: Boolean)(s: Boolean): String =
    if (anyFailures && s) Amber
    else if (!anyFailures && s) Green
    else Red

  private def atLeast(s: String): String => Boolean =
    s match {
      case Red => (s2: String) => true
      case Amber => (s2: String) => s2 == Amber || s2 == Green
      case Green => (s2: String) => s2 == Green
    }

  /** Equivalent to `quorum(1)`. */
  def any(signals: Seq[String]): String = quorum(1)(signals)

  /** Equivalent to `quorum(signals.length)`. */
  def all(signals: Seq[String]): String = quorum(signals.length)(signals)

  /**
   * Succeed if at least `k` inputs are `Green`/`Amber`. Succeed with
   * `Green` if no inputs are `Red`, and `Amber` if at least one input
   * is `Red`.
   */
  def quorum(k: Int)(signals: Seq[String]): String = {
    val ok = signals.filter(atLeast(Amber)).length >= k
    downgrade(signals.exists(_ == Red))(ok)
  }

  /** Equivalent to `fraction(.5 + epsilon)`. */
  def majority(signals: Seq[String]): String =
    fraction(.5000000001)(signals)

  /**
   * Succeed if at least `d * signals.length` inputs are `Green` / `Amber`.
   * Succeed with `Green` if no inputs are `Red`, and `Amber` if at least one input is `Red`.
   */
  def fraction(d: Double)(signals: Seq[String]): String = {
    if (!(d >= 0 && d <= 1.0))
      sys.error("d must be between 0 and 1, inclusive, was: " + d)
    if (signals.isEmpty) Green
    else downgrade(signals.exists(_ == Red)) {
      signals.filter(atLeast(Amber)).length.toDouble / signals.length >= d
    }
  }
}
