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

import org.scalatest.{FlatSpec,Matchers}
import scala.concurrent.duration._
import scalaz.stream.Process
import java.net.URI

class PipelineSpec extends FlatSpec with Matchers {
  import PlatformEvent._
  import Chemist.{Flow,Context}
  import Sharding.Distribution
  import Pipeline.{contextualise,transform}
  import Fixtures._

  implicit class AsTarget(s: String){
    def target: Target =
      Target(java.util.UUID.randomUUID.toString, new URI(s))
  }

  implicit class AsNewTargetFlow(targets: List[Target]){
    def flow: Flow[PlatformEvent] =
      Process.emitAll(targets).map(t => Context(d1, NewTarget(t)))
  }

  implicit class PrettyPrintDistribution(d: Distribution){
    def pretty(): Unit =
      d.toList.foreach { case (key,value) =>
        println(key)
        value.foreach { t =>
          println(s"    $t")
        }
      }
  }

  val d1 = Distribution.empty
    .insert(flask01, Set.empty)
    .insert(flask02, Set.empty)

  val t1 = List(
    "http://localhost:4001/stream/previous".target,
    "http://localhost:4001/stream/now?type=%22String%22".target,
    "http://localhost:4002/stream/previous".target,
    "http://localhost:4002/stream/now?type=%22String%22".target,
    "http://localhost:4003/stream/previous".target,
    "http://localhost:4003/stream/now?type=%22String%22".target,
    "http://localhost:4004/stream/previous".target,
    "http://localhost:4004/stream/now?type=%22String%22".target
  )

  val t2 = List(
    "http://localhost:4005/stream/previous".target,
    "http://localhost:4005/stream/now?type=%22String%22".target,
    "http://localhost:4006/stream/previous".target,
    "http://localhost:4006/stream/now?type=%22String%22".target
  )

  /************************ plan checking ************************/

  it should "correctly distribute the work to one of the flasks" in {
    val p1: Flow[PlatformEvent] =
      List("http://localhost:8888/stream/previous".target).flow

    (p1.map(transform(TestDiscovery, RandomSharding)).runLast.run
      .get.value match {
        case Distribute(d) => d.values.flatMap(identity).length
        case _ => 0
      }) should equal(1)
  }

  // this is a little lame
  it should "produce a plan for every input target" in {
    val accum: List[Plan] =
      t1.flow.map(transform(TestDiscovery, RandomSharding))
      .scan(List.empty[Plan])((a,b) => a :+ b.value)
      .runLast.run
      .toList
      .flatten
    accum.length should equal (t1.length)
  }

  /************************ handlers ************************/

  import Pipeline.handle

  "handle.newFlask" should "correctly redistribute work" in {
    val d = Distribution.empty
      .insert(flask01, t1.toSet)
      .insert(flask02, t2.toSet)

    val (n, r) = handle.newFlask(flask03, LFRRSharding)(d)

    Sharding.shards(n).size should equal (d.keys.size + 1)
    Sharding.targets(n).size should equal (t1.size + t2.size)

    // println("\n\n >>>>> origional")
    // d.pretty()

    // println("\n\n >>>>> stopping")
    // r.stop.pretty()

    // println("\n\n >>>>> starting")
    // r.start.pretty()

    // println("\n\n >>>>> distribution")
    // n.pretty()
  }
}