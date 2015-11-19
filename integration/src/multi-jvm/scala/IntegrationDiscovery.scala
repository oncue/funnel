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
package integration

import scalaz.concurrent.Task
import chemist.{FlaskID,Flask,TargetID,Target,Discovery,Location}
import java.util.UUID.randomUUID

class IntegrationDiscovery extends Discovery {
  def listAllFlasks: Task[Seq[Flask]] =
    listActiveFlasks

  def listActiveFlasks: Task[Seq[Flask]] =
    Task.now(IntegrationFixtures.flasks)

  private val randomids: Map[TargetID, Set[Target]] =
    IntegrationFixtures.targets.map(t =>
      TargetID(randomUUID.toString) -> Set(t)
    ).toMap

  def listTargets: Task[Seq[(TargetID, Set[Target])]] =
    Task.now(randomids.toSeq)

  def listUnmonitorableTargets: Task[Seq[(TargetID, Set[Target])]] = Task.now(Seq.empty)

  def lookupFlask(id: FlaskID): Task[Flask] =
    for {
      a <- listActiveFlasks
    } yield a.find(_.id == id).getOrElse(sys.error("No flask found with that ID."))

  def lookupTarget(id: TargetID): Task[Seq[Target]] =
    Task.now(randomids.get(id).map(_.toSeq).toSeq.flatten)

  def lookupTargets(id: TargetID): Task[Set[Target]] =
    Task.now(randomids.get(id).toSet.flatten)
}
