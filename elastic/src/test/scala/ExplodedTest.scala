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
package elastic

import org.scalacheck.{Properties => P, _}

object ExplodedTest extends P("elastic") {
  val genName = Gen.oneOf("k1", "k2")
  val genHost = Gen.oneOf("h1", "h2")

  val genKey = for {
   n <- genName
   h <- genHost
  } yield Key[Stats](n, Units.Count, "description", Map(AttributeKeys.source -> h))

  val datapoint = for {
    k <- genKey
    d <- Gen.posNum[Double]
  } yield Option(Datapoint(k, d))

  val E = ElasticExploded(Monitoring.default)

  import E._

  // At least one group per key/host pair. I.e. no data is lost.
  property("elasticGroupTop") = Prop.forAll(Gen.listOf(datapoint)) { dps =>
    val gs = elasticGroup(List("k"))(dps ++ dps)
    val sz = gs.map(_.mapValues(_.size).values.sum).sum
    sz >= dps.size
  }

  // Emits as few times as possible
  property("elasticGroupBottom") = Prop.forAll(Gen.listOf(datapoint)) { dps =>
    val noDups = dps.groupBy(_.get.key).mapValues(_.head).values
    elasticGroup(List("k"))(noDups ++ noDups).size == 1 || dps.size == 0
  }

  property("elasticUngroup") = Prop.forAll(Gen.listOf(datapoint)) { dps =>
    val gs = elasticGroup(List("k"))(dps ++ dps)
    val ug = elasticUngroup("flask", "flask")(gs)
    ug.forall(_.isObject)
  }
}
