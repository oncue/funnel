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

import scala.concurrent.duration._
import org.scalatest.{ FlatSpec, Matchers }
import scalaz.concurrent.Strategy.Executor
import scalaz.concurrent.Task
import scalaz.stream._

class ExplodedSpec extends FlatSpec with Matchers {
  import Elastic.lift

  val scheduler = Monitoring.schedulingPool
  val S = Executor(Monitoring.serverPool)
  val E = ElasticExploded(Monitoring.default)

  "elasticGroup" should "emit on timeout" in {
    val cfg = ElasticCfg("localhost", "index", "type", "dateFormat", "template", None, List("k"), 5.seconds, 5.seconds)
    val dp1 = Datapoint[Any](Key[Double]("k1", Units.Count, "description", Map(AttributeKeys.source -> "h1")), 3.14)
    val dp2 = Datapoint[Any](Key[Double]("k2", Units.Count, "description", Map(AttributeKeys.source -> "h2")), 2.17)
    val dps: Process[Task, Option[Datapoint[Any]]] = (Process(dp1) ++ Process(dp2)).map(Option.apply)
    val timeout = time.sleep(5.seconds)(S, scheduler) ++
      Process(Option.empty[Datapoint[Any]]) ++
      time.sleep(5.seconds)(S, scheduler) ++
      Process(Option.empty[Datapoint[Any]])
    val input = timeout.wye(dps ++ time.sleep(15.seconds)(S, scheduler))(wye.merge)(S).translate(lift)
    val ogs = input |> E.elasticGroup(List("k"))
    val result = ogs.runLast.run(cfg).run
    result should be ('defined)
    val gs = result.get
    gs.size shouldBe >= (dps.runLog.run.size)
  }
}

