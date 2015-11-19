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
package aws

import org.scalatest.{FlatSpec, Matchers, BeforeAndAfterAll}
import scalaz._, Scalaz._
import LocationIntent._, NetworkScheme._
import zeromq.TCP

class DiscoverySpec extends FlatSpec with Matchers with BeforeAndAfterAll {
  val t1 = Some("http://@host:5555/stream/previous")
  val t2 = Some("http://@host:@port/stream/now?kind=traffic")
  val t3 = Some("zeromq+tcp://@host:7390/")

  val allTemplates = (t1 :: t2 :: t3 :: Nil).map(x => LocationTemplate(x.get))

  val D = new AwsDiscovery(null, null, null, allTemplates)

  private def make(template: Option[String], int: LocationIntent = Mirroring): String \/ Location =
    D.toLocation("foo.internal", "local", template, int)

  private def expct(
    port: Int,
    int: LocationIntent = Mirroring,
    sch: NetworkScheme = NetworkScheme.Http,
    tmpls: List[LocationTemplate] = allTemplates): String \/ Location =
    Location(
      host = "foo.internal",
      port = port,
      datacenter = "local",
      protocol = sch,
      intent = int,
      templates = tmpls).right

  it should s"prove '${t1.get}' can be turned into a Location" in {
    make(t1) should equal (expct(5555, tmpls = allTemplates.take(2)))
  }

  it should s"prove '${t3.get}' can be turned into a Location" in {
    make(t3, Mirroring) should equal (
      expct(7390, Mirroring, Zmtp(TCP), allTemplates.last :: Nil))
  }

  // this will fail because `toLocation` delibritly does not specifiy @port
  // and java.net.URI will get conffused about the @ in the string, so we
  // just fail fast here.
  it should s"prove '${t2.get}' cannot be turned into a location" in {
    make(t2).isLeft should equal (true)
  }

  it should "fail when no template is supplied" in {
    make(None).isLeft should equal (true)
  }

}
