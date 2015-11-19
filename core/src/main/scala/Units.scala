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

sealed trait Units

object Units {

  // NB: Duration, Bytes, Count, Ratio can be units associated
  // with a Stats metric, or a single-value metric, hence
  // the intersection type

  private def capitalize(s: String): String =
    s.headOption.map(hd => hd.toString.toUpperCase + s.tail.toLowerCase).getOrElse(s)

  case class Duration(granularity: TimeUnit) extends Units { //[Double with funnel.Stats] {
    override def toString = capitalize(granularity.toString)
  }
  case class Bytes(base: Base) extends Units {
    import Base._
    override def toString = base match {
      case Zero => "Bytes"
      case Kilo => "Kilobytes"
      case Mega => "Megabytes"
      case Giga => "Gigabytes"
    }
  }
  case object Count extends Units
  case object Ratio extends Units
  case object TrafficLight extends Units
  case object Healthy extends Units
  case object Load extends Units

  case object None extends Units

  val Milliseconds = Duration(TimeUnit.MILLISECONDS)
  val Seconds = Duration(TimeUnit.SECONDS)
  val Minutes = Duration(TimeUnit.MINUTES)
  val Hours = Duration(TimeUnit.HOURS)
  val Megabytes = Bytes(Base.Mega)

  sealed trait Base
  object Base {
    case object Zero extends Base // 10^0 = 1
    case object Kilo extends Base // 10^3
    case object Mega extends Base // 10^6
    case object Giga extends Base // 10^9
  }

  /**
   * Returns the default value associated with the given type and
   * units. This is the value that should be assumed if a metric
   * of the given type and units cannot be properly mirrored (say,
   * because the node being contacted is down). See `Monitoring.mirror`
   * and `Monitoring.mirrorAll` for how this is used.
   */
  def default[O](t: Reportable[O], u: Units): Option[O] = (t, u) match {
    case (Reportable.B, Healthy) => Some(false)
    case (Reportable.S, TrafficLight) => Some(funnel.TrafficLight.Red)
    case _ => scala.None
  }

}
