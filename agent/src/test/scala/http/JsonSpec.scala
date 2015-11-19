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

import org.scalatest._
import scalaz._

class JsonSpec extends FlatSpec with Matchers {
  import argonaut._, Argonaut._
  import JSON._
  import scala.concurrent.duration._

  implicit val instruments = new Instruments()

  it should "decode json into an ArbitraryMetric" in {
    val in = """{"name":"ntp/whatever","kind":"counter","value": "123"}"""
    Parse.decodeEither[ArbitraryMetric](in) should equal (
      \/-(ArbitraryMetric("ntp/whatever", InstrumentKinds.Counter, Option("123"))))
  }

  it should "decode json into a InstrumentRequest" in {
    val in = """{"cluster":"foo-whatever","metrics":[{"name":"ntp/whatever","kind":"timer","value":"0.1234"}]}"""
    Parse.decodeOption[InstrumentRequest](in).get.timers.length should equal (1)
    // should be idempotent
    Parse.decodeOption[InstrumentRequest](in).get.timers.length should equal (1)
  }

}
