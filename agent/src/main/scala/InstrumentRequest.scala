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
package agent

case class InstrumentRequest(
  cluster: String,
  counters: List[ArbitraryMetric] = Nil,
  timers: List[ArbitraryMetric] = Nil,
  stringGauges: List[ArbitraryMetric] = Nil,
  doubleGauges: List[ArbitraryMetric] = Nil
)
object InstrumentRequest {
  import InstrumentKinds._

  def apply(cluster: String, metric: ArbitraryMetric): InstrumentRequest =
    metric.kind match {
      case Counter     => InstrumentRequest(cluster, counters = metric :: Nil)
      case Timer       => InstrumentRequest(cluster, timers = metric :: Nil)
      case GaugeDouble => InstrumentRequest(cluster, doubleGauges = metric :: Nil)
    }

  def apply(cluster: String, metrics: ArbitraryMetric*): InstrumentRequest =
    InstrumentRequest(cluster,
      counters     = metrics.filter(_.kind == Counter).toList,
      timers       = metrics.filter(_.kind == Timer).toList,
      stringGauges = metrics.filter(_.kind == GaugeString).toList,
      doubleGauges = metrics.filter(_.kind == GaugeDouble).toList
    )
}
