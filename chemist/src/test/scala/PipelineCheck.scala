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
import java.util.UUID

import scalaz.==>>
import scalaz.concurrent.Task

import Chemist.Context
import LocationIntent._
import PlatformEvent._
import Sharding.Distribution

import org.scalacheck._
import Gen.{ alphaNumChar, listOfN, oneOf }
import Arbitrary.arbitrary
import Prop.{ falsified, forAll, passed, BooleanOperators }

class StaticDiscovery(targets: Map[TargetID, Set[Target]], flasks: Map[FlaskID, Flask]) extends Discovery {
  def listTargets: Task[Seq[(TargetID, Set[Target])]] = Task.delay(targets.toSeq)
  def listUnmonitorableTargets: Task[Seq[(TargetID, Set[Target])]] = Task.now(Seq.empty)
  def listAllFlasks: Task[Seq[Flask]] = Task.delay(flasks.values.toSeq)
  def listActiveFlasks: Task[Seq[Flask]] = Task.delay(flasks.values.toSeq)
  def lookupFlask(id: FlaskID): Task[Flask] = Task.delay(flasks(id))	// Can obviously cause the Task to fail
  def lookupTargets(id: TargetID): Task[Set[Target]] = Task.delay(targets(id))	// Can obviously cause the Task to fail
}

object PipelineCheck extends Properties("Pipeline") {
  import Fixtures._

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

  property("newFlask works") = forAll { (f: Flask, s: Sharder, d: Distribution) =>
    val (nd, _) = Pipeline.handle.newFlask(f, s)(d)
    (!Sharding.shards(d).contains(f)) ==>
    ("The existing Distribution does not contain the Flask" |:
      !d.keySet.contains(f)) &&
    ("The new Distribution contains the Flask" |:
      nd.keySet.contains(f)) &&
    ("The existing and new Distributions have the same Targets" |:
      Sharding.targets(d) == Sharding.targets(nd))
  }

  property("newTarget works") = forAll { (t: Target, s: Sharder, d: Distribution) =>
    val nd = Pipeline.handle.newTarget(t, s)(d)
    ("The existing Distribution does not contain the Target" |:
      !Sharding.targets(d).contains(t)) &&
     ("The new Distribution contains the Target" |:
       (d.size > 0) ==> Sharding.targets(nd).contains(t))
  }

  property("transform works") = forAll { (sd: StaticDiscovery, s: Sharder, c: Context[PlatformEvent]) =>
    val d: Distribution = c.distribution
    val e: PlatformEvent = c.value
    val cp: Context[Plan] = Pipeline.transform(sd, s)(c)
    val nd: Distribution = cp.distribution
    val p: Plan = cp.value

    e match {
      case NewTarget(t) => p match {
        case Distribute(w) =>
          ("The old Distribution does not contain the new Target" |: !Sharding.targets(d).contains(t)) &&
          (d.size > 0) ==>
            ("The Work does contain the new Target" |: Sharding.targets(w).contains(t))
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
        case Produce(tasks) =>
          val ts = tasks.run.map { _ match {
            case NewTarget(t) => t
            case _ => throw new RuntimeException("Not all Produced PlatformEvents were NewTargets")
          }}.toSet
          val nts = Sharding.targets(nd)
          val ots = Sharding.targets(d)
          (Sharding.shards(d).contains(f)) ==>
          (s"The new Distribution's Targets ($nts) plus the Produced Targets ($ts) equal the old Distribution's Targets ($ots)" |:
            nts ++ ts == ots) &&
          ("The terminated Flask is not in the new Distribution" |: !Sharding.shards(nd).contains(f))
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
