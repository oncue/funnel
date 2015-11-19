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

/**
 * Hard-coded attribute key names. These really should be types, not strings.
 * But since these things are going over the wire, we should put them in one
 * place so that every place that refers to them agrees.
 */
object AttributeKeys {
  val cluster = "cluster"
  val source = "source"
  val kind = "kind"
  val experimentID = "experiment_id"
  val experimentGroup = "experiment_group"
  val units = "units"
  val edge = "edge"
}
