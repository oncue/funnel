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

import journal.Logger
import org.scalatest.{FlatSpec, Matchers, BeforeAndAfterAll}
import funnel.Monitoring
import funnel.http.MonitoringServer
import scalaz.==>>
import scalaz.std.string._
import java.net.URI
import Target._
import org.scalactic.TypeCheckedTripleEquals

class ShardingSpec extends FlatSpec with Matchers with TypeCheckedTripleEquals {

  import Sharding.Distribution

  val localhost: Location =
    Location(
      host = "127.0.0.1",
      port = 5775,
      datacenter = "local",
      protocol = NetworkScheme.Http,
      intent = LocationIntent.Mirroring,
      templates = Seq.empty)

  implicit def tuple2target(in: (String,String)): Target =
    Target(in._1, new URI(in._2))

  def fakeFlask(id: String) = Flask(FlaskID(id), localhost)

  val d1: Distribution = ==>>(
    (fakeFlask("a"), Set(("z","http://one.internal"))),
    (fakeFlask("d"), Set(("y","http://two.internal"), ("w","http://three.internal"), ("v","http://four.internal"))),
    (fakeFlask("c"), Set(("x","http://five.internal"))),
    (fakeFlask("b"), Set(("z","http://two.internal"), ("u","http://six.internal")))
  )

  val i1: Set[Target] = Set(
    ("w", "http://three.internal"),
    ("u", "http://eight.internal"),
    ("v", "http://nine.internal"),
    ("z", "http://two.internal")
  )

  val i2: Set[Target] = Set(
    ("w", "http://alpha.internal"),
    ("u", "http://beta.internal"),
    ("v", "http://omega.internal"),
    ("z", "http://gamma.internal"),
    ("p", "http://zeta.internal"),
    ("r", "http://epsilon.internal"),
    ("r", "http://theta.internal"),
    ("r", "http://kappa.internal"),
    ("z", "http://omicron.internal")
  )

  it should "correctly calculate how the new request should be sharded over known flasks" in {
    val (s, newdist) = LFRRSharding.distribution(i1)(d1)
    s.map {
      case (x,y) => x.id.value -> y
    }.toSet should === (Set(
      "a" -> Target("u",new URI("http://eight.internal")),
      "c" -> Target("v",new URI("http://nine.internal"))))

    val (s2,newdist2) = LFRRSharding.distribution(i2)(d1)
    s2.map(_._2).toSet should === (Set(
      Target("v",new URI("http://omega.internal")),
      Target("w",new URI("http://alpha.internal")),
      Target("r",new URI("http://epsilon.internal")),
      Target("z",new URI("http://gamma.internal")),
      Target("u",new URI("http://beta.internal")),
      Target("z",new URI("http://omicron.internal")),
      Target("r",new URI("http://kappa.internal")),
      Target("r",new URI("http://theta.internal")),
      Target("p",new URI("http://zeta.internal"))))
  }
}
