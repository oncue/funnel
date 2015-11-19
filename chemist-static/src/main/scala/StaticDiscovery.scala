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

class StaticDiscovery(targets: Map[TargetID, Set[Target]], flasks: Map[FlaskID, Flask]) extends Discovery {
  def listTargets: Task[Seq[(TargetID, Set[Target])]] = Task.delay(targets.toSeq)
  def listUnmonitorableTargets: Task[Seq[(TargetID, Set[Target])]] = Task.now(Seq.empty)
  def listAllFlasks: Task[Seq[Flask]] = Task.delay(flasks.values.toSeq)
  def listActiveFlasks: Task[Seq[Flask]] = Task.delay(flasks.values.toSeq)
  def lookupFlask(id: FlaskID): Task[Flask] = Task.delay(flasks(id))	// Can obviously cause the Task to fail
  def lookupTargets(id: TargetID): Task[Set[Target]] = Task.delay(targets(id))	// Can obviously cause the Task to fail
}
