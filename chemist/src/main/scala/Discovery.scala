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

trait Discovery {
  def listTargets: Task[Seq[(TargetID,Set[Target])]]
  def listUnmonitorableTargets: Task[Seq[(TargetID,Set[Target])]]
  def listAllFlasks: Task[Seq[Flask]]
  def listActiveFlasks: Task[Seq[Flask]]
  def lookupTargets(id: TargetID): Task[Set[Target]]
  def lookupFlask(id: FlaskID): Task[Flask]

  ///////////////////////////// filters /////////////////////////////

  import Classification._

  /**
   * Find all the flasks that are currently classified as active.
   * @see funnel.chemist.Classifier
   */
  def isActiveFlask(c: Classification): Boolean =
    c == ActiveFlask

  /**
   * Find all the flasks - active and inactive.
   * @see funnel.chemist.Classifier
   */
  def isFlask(c: Classification): Boolean =
    c == ActiveFlask ||
    c == InactiveFlask

  /**
   * Find all chemist instances that are currently classified as active.
   * @see funnel.chemist.Classifier
   */
  def isActiveChemist(c: Classification): Boolean =
    c == ActiveChemist

  /**
   * The reason this is not simply the inverted version of `isActiveFlask`
   * is that when asking for targets, we specifically do not want any
   * Flasks, active or otherwise, because mirroring a Flask from another
   * Flask risks a cascading failure due to key amplication (essentially
   * mirroring the mirrorer whilst its mirroring etc).
   *
   * To mittigate this, we specifically call out anything that is not
   * classified as an `ActiveTarget`
   *
   * @see funnel.chemist.Classifier
   */
  def isMonitorable(c: Classification): Boolean =
    c == ActiveTarget ||
    c == ActiveChemist ||
    c == InactiveChemist
}
