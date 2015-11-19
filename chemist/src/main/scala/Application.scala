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

/**
 * Application is meant to represent the logically unique deployment
 * of a given application. For example, foo service 1.2.3 deployment A,
 * and foo service 1.2.3 deployment B and foo service 2.3.4 are all
 * unique applications as far as chemist knows (this is needed for other
 * parts of the system to support experimentation etc)
 */
case class Application(
  name: String,
  version: String,
  qualifier: Option[String]
){
  override def toString: String =
    s"$name-$version${qualifier.map("-"+_).getOrElse("")}"
}
