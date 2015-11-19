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
package funnel {

  package object instruments extends Instruments() with DefaultKeys {
    val instance = this

    Clocks.instrument(this)
    JVM.instrument(this)
    Sigar(this).foreach { _.instrument }
  }
}

import scalaz.FreeAp
import scalaz.concurrent.Task
import scalaz.stream.Process
import scalaz.Monoid
import com.twitter.algebird.Group

package object funnel {
  import scala.concurrent.duration._

  type KeySet[A] = OneOrThree[Key[A]]
  type Metric[A] = FreeAp[KeySet,A]

  type DatapointParser    = java.net.URI => Process[Task,Datapoint[Any]]
  type ClusterName        = String
  type ContinuousGauge[A] = Gauge[Continuous[A],A]

  val defaultWindow = 1 minute

  implicit def GroupMonoid[A](implicit G: Group[A]): Monoid[A] = new Monoid[A] {
    val zero = G.zero
    def append(a: A, b: => A) = G.plus(a, b)
  }

  implicit val dontUseTheDefaultStrategy: scalaz.concurrent.Strategy = null
  implicit val theDefaultStrategyCausesProblems: scalaz.concurrent.Strategy = null
}
