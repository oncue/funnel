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

import org.scalatest.{FlatSpec, Matchers}
import scalaz.==>>
import java.net.URI
import org.scalactic.TypeCheckedTripleEquals

class ShardingSpec extends FlatSpec with Matchers with TypeCheckedTripleEquals {

  import Sharding.Distribution

  def location(id: String): Location =
    Location(
      host = s"$id.host",
      port = 5775,
      datacenter = "local",
      protocol = NetworkScheme.Http,
      intent = LocationIntent.Mirroring,
      templates = Seq.empty)

  implicit def tuple2target(in: (String,String)): Target =
    Target(in._1, new URI(in._2))

  def fakeFlask(id: String) = Flask(FlaskID(id), location(id))

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

  "LFRRSharding" should "correctly calculate how the new request should be sharded over known flasks" in {
    val newdist = LFRRSharding.distribution(i1)(d1)

    //Expect that 2 new targets got assigned to 2 least used flasks (one target each):
    //  Target("u",new URI("http://eight.internal"))
    //  Target("v",new URI("http://nine.internal")))
    newdist.mapKeys(_.id).lookup(FlaskID("a")).get.size should equal(2)
    newdist.mapKeys(_.id).lookup(FlaskID("c")).get.size should equal(2)

    //adding new 9 targets. Expect almost equal distribution of work
    val newdist2 = LFRRSharding.distribution(i2)(d1)
    newdist2.values.map(_.size).sorted should equal(List(3, 4, 4, 5))
  }

  "FlaskStreamsSharder" should "ignore non-flask streams" in {
    val (newdist, rest) = FlaskStreamsSharder.distribute(i1)(d1)

    newdist should equal(d1)
    rest should equal(i1)
  }

  it should "assign flask targets to flasks" in {
    val flask = d1.keySet.toList.head
    val flaskTarget: Target = ("zz", s"http://${flask.location.host}")

    val (newdist, rest) = FlaskStreamsSharder.distribute(Set(flaskTarget) ++ i1)(d1)

    newdist.lookup(flask).get.contains(flaskTarget) should equal(true)
    rest should equal(i1)
  }
}
