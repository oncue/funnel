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
package zeromq

class Version(val number: Int) extends AnyVal {
  override def toString = number.toString
}
object Versions {
  def fromInt(i: Int): Version =
    i match {
      case 1 => v1
      case 2 => v2
      case _ => unknown
    }

  def fromString(s: String): Version =
    try fromInt(s.toInt)
    catch {
      case e: NumberFormatException => unknown
    }

  val all: Seq[Version] =
    v1 :: v2 :: Nil

  /**
   * Of all protocol versions, what are the current versions we
   * support.
   *
   * TIM: consider somehow taking this as a configuration param
   * at a later date so that its not hard coded.
   */
  val supported: Seq[Version] =
    v1 :: Nil

  // JSON over ZMTP
  lazy val v1 = new Version(1)

  // Binary scodec over ZMTP
  lazy val v2 = new Version(2)

  // Something went really wrong...
  lazy val unknown = new Version(0)
}
