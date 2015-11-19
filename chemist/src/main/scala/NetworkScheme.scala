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

import zeromq.Protocol

/**
 * NetworkScheme represents the mechinism that will be used to actually
 * fetch metrics from a given `Target`. Most URIs have a single, opqauqe
 * URI scheme (see rfc2396), its legal to encode specilizations using the
 * '+' character. Subsequently, we encode the ZMTP "mode" specilization
 * in this manner. Examples are:
 *
 * `http`
 * `zeromq+tcp`
 * `zeromq+ipc`
 *
 * Doing this ensures that if we add a different transport system at a later
 * date we did not totally occupy the TCP namespace w/r/t URI declerations.
 */
sealed trait NetworkScheme {
  def scheme: String
  override def toString: String = scheme
}

object NetworkScheme {
  val all: Seq[NetworkScheme] = Protocol.all.map(Zmtp(_)) :+ Http

  def fromString(in: String): Option[NetworkScheme] =
    all.find(_.scheme == in.toLowerCase.trim)

  case class Zmtp(p: Protocol) extends NetworkScheme {
    val scheme = s"zeromq+${p.toString}"
  }
  case object Http extends NetworkScheme {
    val scheme = "http"
  }
}
