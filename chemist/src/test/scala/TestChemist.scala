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
import scalaz.syntax.kleisli._
import scalaz.Applicative

class TestChemist extends Chemist[TestPlatform]{

  def filterTargets(instances: Seq[(TargetID, Set[Target])]): ChemistK[(Seq[(TargetID, Set[Target])], Seq[(TargetID, Set[Target])])] =
    Applicative[ChemistK].point((instances, Seq()))

  def init: ChemistK[Unit] =
    Task.now(()).liftKleisli

}
