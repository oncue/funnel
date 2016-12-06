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
import scalaz.stream.Process
import java.net.URI
import scalaz.concurrent.Task

class PipelineSpec extends FlatSpec with Matchers {
  import PlatformEvent._
  import Chemist.{Flow,Context}
  import Sharding.Distribution
  import Pipeline.transform
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

  private def sizePlans(lst: List[Plan]): Int =
    lst.map {
      case Redistribute(stop, start) =>
        Sharding.targets(stop).size + Sharding.targets(start).size
      case Distribute(d) =>
        Sharding.targets(d).size
      case _ => 0
    }.sum

  private val gatherIdentity = (in: Distribution) => Task.delay(in)

  /************************ plan checking ************************/

  "transform" should "correctly distribute the work to one of the flasks" in {
    val t = "http://localhost:8888/stream/previous".target
    val e: Context[PlatformEvent] = Context(d1, NewTarget(t))

    transform(TestDiscovery, RandomSharding, gatherIdentity)(e).unsafePerformSync.value match {
      case Redistribute(stop, start) =>
        stop.values.flatten.size should equal(0)
        start.values.flatten.size should equal(1)
        start.values.flatten.toSet should equal(Set(t))
      case _ => fail("unexpected command")
    }
  }

  it should "produce a ONE command to monitor for every input target" in {
    val accum: List[Plan] =
      t1.flow.map(transform(TestDiscovery, RandomSharding, gatherIdentity))
      .scan(List.empty[Plan])((a,b) => a :+ b.unsafePerformSync.value)
      .runLast.unsafePerformSync
      .toList
      .flatten

    accum.length should equal (t1.length)
    sizePlans(accum) should equal(t1.length)
  }

  it should "not produce commands for streams we already monitor (full discovery)" in {
    val targetsInDiscovery: Set[Target] = t1.toSet ++ t2.toSet

    val d = Distribution.empty.insert(flask01, t1.toSet).insert(flask02, Set.empty)

    val e = AllTargets(targetsInDiscovery.toSeq)

    val p: Flow[PlatformEvent] = Process.emit(Context(d, e))

    val accum: List[Plan] = p.evalMap[Task, Context[Plan]](e => transform(TestDiscovery, RandomSharding, gatherIdentity)(e))
          .scan(List.empty[Plan])((a,b) => a :+ b.value)
          .runLast.unsafePerformSync
          .toList
          .flatten

    accum.length should equal (1) //single redistribute action
    sizePlans(accum) should equal (t2.size) //should only include what was not part of incoming distribution
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
    //expect we restart everything we stop
    Sharding.targets(r.stop).size should equal(Sharding.targets(r.start).size)
  }

  "handle.rediscovery" should "correctly stop work" in {
    val d = Distribution.empty
      .insert(flask01, t1.toSet)
      .insert(flask02, t2.toSet)

    val (n, r) = handle.rediscovery(Set.empty, d)(LFRRSharding)

    Sharding.shards(n).size should equal (d.keys.size)
    Sharding.targets(n).size should equal (0)
    Sharding.targets(r.start).size should be (0)
    Sharding.targets(r.stop).size should be (t1.size + t2.size)
  }

  it should "correctly start new work" in {
    val (n, r) = handle.rediscovery(t2.toSet, d1)(LFRRSharding)

    Sharding.shards(n).size should equal (d1.keys.size)
    Sharding.targets(n).size should equal (t2.size)
    Sharding.targets(r.stop).size should be (0)
    Sharding.targets(r.start).size should be (t2.size)
  }

  it should "correctly rebalance work" in {
    //distribution is mix of targets that will stay and targets that will disappear
    val d = Distribution.empty
      .insert(flask01, t1.take(3).toSet ++ t2.take(1).toSet)
      .insert(flask02, t1.takeRight(3).toSet ++ t2.takeRight(1).toSet)

    val (n, r) = handle.rediscovery(t2.toSet, d)(LFRRSharding)

    Sharding.shards(n).size should equal (d.keys.size)
    Sharding.targets(n).size should equal (t2.size)
    Sharding.targets(r.stop).size should be (3 + 3)
    Sharding.targets(r.start).size should be (t2.size - 1 - 1)
  }
}