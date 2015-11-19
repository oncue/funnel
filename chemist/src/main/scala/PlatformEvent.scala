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

sealed abstract class PlatformEvent {
  val time = new java.util.Date
}

object PlatformEvent {
  final case class NewTarget(target: Target) extends PlatformEvent
  final case class NewFlask(flask: Flask) extends PlatformEvent
  final case class TerminatedTarget(u: URI) extends PlatformEvent
  final case class TerminatedFlask(f: FlaskID) extends PlatformEvent
  final case object NoOp extends PlatformEvent
}
