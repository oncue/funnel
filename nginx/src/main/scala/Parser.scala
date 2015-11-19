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
package nginx

import util.matching.Regex
import scalaz.\/
import journal.Logger

/**
 * This parser is designed to extract the meaingfull values
 * from the output generated by the stub_status nginx module:
 * http://nginx.org/en/docs/http/ngx_http_stub_status_module.html
 */
object Parser {

  private val log = Logger[Parser.type]

  private[nginx] val activeR = """^Active connections:\s+(\d+)\s+""".r
  private[nginx] val handledR = """^\s+(\d+)\s+(\d+)\s+(\d+)\s+""".r
  private[nginx] val currentR = """^Reading:\s+(\d+).*Writing:\s+(\d+).*Waiting:\s+(\d+)\s+""".r

  def parse(input: String): Throwable \/ Stats = \/.fromTryCatchNonFatal {
    val Array(line1, line2, line3, line4) = input.split('\n')

    val activeR(connections)                 = line1
    val handledR(accepts, handled, requests) = line3
    val currentR(reading, writing, waiting)  = line4

    Stats(
      connections.toLong,
      accepts.toLong,
      handled.toLong,
      requests.toLong,
      reading.toLong,
      writing.toLong,
      waiting.toLong)
  }
}
