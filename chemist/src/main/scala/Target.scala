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

import scalaz.Order
import scalaz.std.tuple._
import scalaz.std.string._
import java.net.URI

case class TargetID(value: String) extends AnyVal

/**
 * A target is a physical thing we would like to monitor in the
 * system. It must have a URI representing its location.
 */
case class Target(
  /* cluster represents the phyiscal deployment. It must be unique
     even if the same software is already deployed (e.g. A-B testing) */
  cluster: ClusterName,
  /* the location this target can be reached. follow the RFC spec for
     guidence on how to properly encode schemes and paths. */
  uri: URI
)

object Target {
  val defaultResources = Set("stream/previous")

  implicit val orderTarget: Order[Target] =
    Order[(String,String)].contramap(t => (t.cluster, t.uri.toString))
}
