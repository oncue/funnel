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
 * A type, `A`, constained to be either `Int`,
 * `Double`, `String`, or `Stats`.
 */
sealed trait Reportable[+A] { self =>
  def read(a: Any): Option[A]
  def description: String
  def cast[B](R: Reportable[B]): Option[Reportable[B]] =
    if (R == this) Some(R)
    else None
}

object Reportable {
  /**
   * used to make sure scalacheck is generating all possiblilites, if
   * you add a new one, please update this
   */
  def all: Seq[Reportable[Any]] = Seq(B,D,S,Stats)

  implicit case object B extends Reportable[Boolean] {
    def read(a: Any): Option[Boolean] =
      try Some(a.asInstanceOf[Boolean])
      catch { case cce: ClassCastException => None }
    def description = "Boolean"
  }
  implicit case object D extends Reportable[Double] {
    def read(a: Any): Option[Double] =
      try Some(a.asInstanceOf[Double])
      catch { case cce: ClassCastException => None }
    def description = "Double"
  }
  implicit case object S extends Reportable[String] {
    def read(a: Any): Option[String] =
      try Some(a.asInstanceOf[String])
      catch { case cce: ClassCastException => None }
    def description = "String"
  }
  implicit case object Stats extends Reportable[funnel.Stats] {
    def read(a: Any): Option[funnel.Stats] =
      try Some(a.asInstanceOf[funnel.Stats])
      catch { case cce: ClassCastException => None }
    def description = "Stats"
  }

  /** Parse a `Reportable` witness from a description. */
  def fromDescription(s: String): Option[Reportable[Any]] = s match {
    case "Boolean" => Some(B)
    case "Double" => Some(D)
    case "String" => Some(S)
    case "Stats" => Some(Stats)
    case _ => None
  }

  // case class Histogram(get: monitoring.Histogram[String]) extends Reportable[monitoring.Histogram[String]]

  def apply[A:Reportable](a: A): A = a
}
