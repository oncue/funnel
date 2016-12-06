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

import java.net.URI
import scalaz.concurrent.Task
import Chemist.Context
import PlatformEvent._
import Sharding.Distribution
import org.scalacheck._
import Gen.oneOf
import Arbitrary.arbitrary
import Prop.{BooleanOperators, falsified, forAll, passed}

case class StaticDiscovery(targets: Map[TargetID, Set[Target]], flasks: Map[FlaskID, Flask]) extends Discovery {
  def inventory: Task[DiscoveryInventory] = Task.delay(
    DiscoveryInventory(targets.toSeq, Seq.empty, flasks.values.toSeq, flasks.values.toSeq)
  )
  def lookupFlask(id: FlaskID): Task[Flask] = Task.delay(flasks(id))	// Can obviously cause the Task to fail
  def lookupTargets(id: TargetID): Task[Set[Target]] = Task.delay(targets(id))	// Can obviously cause the Task to fail
}

object PipelineCheck extends Properties("Pipeline") {
  import Fixtures._

  implicit class PrettyPrintDistribution(d: Distribution) {
    def pretty(): String =
      s"=========== flasks=${d.keySet.size} targets=${d.values.map(_.size).sum}\n" +
      d.toList.map { case (key,value) =>
        s"$key\n" + value.map(t => s"    $t").mkString("\n")
      }.mkString("\n") + "\n===================\n"
  }

  def genTargetID = for {
    id <- alphaNumStr
  } yield TargetID(id)
  implicit lazy val arbTargetID: Arbitrary[TargetID] = Arbitrary(genTargetID)

  def genDiscovery = for {
    tpairs <- arbitrary[List[(TargetID, Set[Target])]]
    flasks <- arbitrary[List[Flask]]
  } yield new StaticDiscovery(tpairs.toMap, flasks.map(f => (f.id, f)).toMap)
  implicit lazy val arbDiscovery: Arbitrary[StaticDiscovery] = Arbitrary(genDiscovery)

  def genNewTarget = for {
    target <- arbitrary[Target]
  } yield NewTarget(target)

  def genNewFlask = for {
    flask <- arbitrary[Flask]
  } yield NewFlask(flask)

  def genTerminatedTarget = for {
    uri <- arbitrary[URI]
  } yield TerminatedTarget(uri)

  def genTerminatedFlask = for {
    flaskID <- arbitrary[FlaskID]
  } yield TerminatedFlask(flaskID)

  def genNoOp = Gen.const(NoOp)

  implicit lazy val arbPlatformEvent: Arbitrary[PlatformEvent] =
    Arbitrary(oneOf(genNewTarget, genNewFlask, genTerminatedTarget, genTerminatedFlask, genNoOp))

  def genContextOfPlatformEvent = for {
    d <- arbitrary[Distribution]
    e <- arbitrary[PlatformEvent]
  } yield Context(d, e)

  implicit lazy val arbContextOfPlatformEvent: Arbitrary[Context[PlatformEvent]] =
    Arbitrary(genContextOfPlatformEvent)

  private val gatherIdentity = (in: Distribution) => Task.delay(in)

  property("newTarget works") = forAll { (t: Target, s: Sharder, d: Distribution) =>
    val (nd, cmd) = Pipeline.handle.newTarget(t, s)(d)
    ("The existing Distribution does not contain the Target" |:
      !Sharding.targets(d).contains(t)) &&
     ("The new Distribution contains the Target" |:
       (d.size > 0) ==> Sharding.targets(nd).contains(t))
  }

  property("transform works") = forAll { (sd: StaticDiscovery, s: Sharder, c: Context[PlatformEvent]) =>
    val d: Distribution = c.distribution
    val e: PlatformEvent = c.value
    val cp: Context[Plan] = Pipeline.transform(sd, s, gatherIdentity)(c).unsafePerformSync
    val nd: Distribution = cp.distribution
    val p: Plan = cp.value

    e match {
      case NewTarget(t) => p match {
        case Redistribute(stop, start) =>
          ("The old Distribution does not contain the new Target" |: !Sharding.targets(d).contains(t)) &&
          ("Nothing to stop" |: stop.isEmpty) &&
          (d.size > 0) ==>
            ("The Work does contain ONLY new Target" |: Sharding.targets(start) == Set(t))
        case _ => falsified
      }
      case NewFlask(f) => p match {
        case Redistribute(stop, start) =>
          (!Sharding.shards(d).contains(f)) ==>
          ("The new Flask is not in the old Distribution" |: !Sharding.shards(d).contains(f)) &&
          ("The new Flask is in the new Distribution" |: Sharding.shards(start).contains(f)) &&
          ("The Targets in the old Distribution are all in the new Distribution" |:
            Sharding.targets(d) == Sharding.targets(nd))
        case _ => falsified
      }
      case NoOp => passed
      case TerminatedFlask(f) => p match {
        case Redistribute(stop, start) =>
          val nts = Sharding.targets(nd)
          val ots = Sharding.targets(d)

          val oldLoad: Option[Set[Target]] = d.mapKeys(_.id).lookup(f)
          //inputs are random => not all of them may be in the discovery and then they will not be reallocated!
          val oldTargets = oldLoad.getOrElse(Set.empty).intersect(sd.targets.values.flatten.toSet)

          oldLoad.nonEmpty ==>
            (s"Targets monitored by old flask should be reassigned if they are alive."
              |: oldTargets.diff(Sharding.targets(start)).isEmpty)
        case _ => falsified
      }
      case TerminatedTarget(t) => p match {
        case Ignore => passed
        case _ => falsified
      }
      case _ => falsified
    }
  }
}
