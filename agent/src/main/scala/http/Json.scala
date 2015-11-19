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
package http

import argonaut._, Argonaut._

case class Request(
  cluster: String,
  metrics: List[ArbitraryMetric]
)

object JSON {
  import InstrumentKinds._
  import concurrent.duration._

  /**
   * {
   *   "cluster": "foo-whatever",
   *   "metrics": [
   *     {
   *       "name": "ntp/whatever",
   *       "kind": "gauge-double",
   *       "value": "0.1234"
   *     }
   *   ]
   * }
   */
  implicit def JsonToInstrumentRequest: DecodeJson[InstrumentRequest] =
    DecodeJson(c => for {
      k <- (c --\ "cluster").as[String]
      m <- (c --\ "metrics").as[List[ArbitraryMetric]]
    } yield InstrumentRequest(
      cluster      = k,
      counters     = m.filter(_.kind == Counter),
      timers       = m.filter(_.kind == Timer),
      stringGauges = m.filter(_.kind == GaugeString),
      doubleGauges = m.filter(_.kind == GaugeDouble)
    ))

  implicit val JsonToArbitraryMetric: DecodeJson[ArbitraryMetric] =
    DecodeJson(c => for {
      n <- (c --\ "name").as[String]
      k <- (c --\ "kind").as[InstrumentKind]
      v <- (c --\ "value").as[String].option
    } yield ArbitraryMetric(n, k, v))

  implicit val JsonToInstrumentKind: DecodeJson[InstrumentKind] = DecodeJson { c =>
    c.as[String].map(_.toLowerCase).flatMap {
      case "counter"      => DecodeResult.ok { Counter }
      case "timer"        => DecodeResult.ok { Timer }
      case "gauge-string" => DecodeResult.ok { GaugeString }
      case "gauge-double" => DecodeResult.ok { GaugeDouble }
    }
  }
}

