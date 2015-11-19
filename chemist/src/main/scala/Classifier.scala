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
package chemist

import scalaz.concurrent.Task

/**
 * Classifier is required by all `Discovery` instances, and
 * should form an important part of discovery, by allowing the
 * system to partition systems in a meaningful manner.
 *
 * @see funnel.chemist.Classification
 */
trait Classifier[A] {
  def task: Task[A => Classification]
}

/**
 * The concept here is that upon discovering instances, they
 * are subsequently classified into a setup of finite groups.
 * These groups can then be used for making higher level
 * choices about what an instance should be used for (or if
 * it should be dropped entirely)
 */
sealed trait Classification

object Classification {
  case object ActiveFlask extends Classification
  case object InactiveFlask extends Classification
  case object ActiveTarget extends Classification
  case object ActiveChemist extends Classification
  case object InactiveChemist extends Classification
  case object Unknown extends Classification
}
