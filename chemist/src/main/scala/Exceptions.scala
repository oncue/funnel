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
package funnel.chemist

case class InstanceNotFoundException(
  instanceId: String,
  kind: String = "instance") extends RuntimeException {
  override val getMessage: String = s"No $kind found with the id: '$instanceId'"
}

case class InvalidLocationException(location: Location) extends RuntimeException {
  override val getMessage: String = s"No hostname is specified for the specified location: $location"
}

case class NotAFlaskException(e: AutoScalingEvent) extends RuntimeException {
  override val getMessage: String = s"The specified scaling event was not a flask $e"
}

case class FlaskMissingSupervision[A](e: A) extends RuntimeException {
  override val getMessage: String = s"The specified flask instance does not appear to have a supervision socket"
}
