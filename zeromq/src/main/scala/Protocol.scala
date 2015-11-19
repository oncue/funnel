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
package zeromq

abstract class Protocol(name: String){
  override def toString: String = name
}
object Protocol {
  lazy val all: Seq[Protocol] =
    TCP :: IPC :: UDP :: InProc :: Pair :: Nil

  def fromString(s: String): Option[Protocol] =
    all.find(_.toString == s.toLowerCase)
}

case object TCP extends Protocol("tcp")
case object IPC extends Protocol("ipc")
case object UDP extends Protocol("udp")
case object InProc extends Protocol("proc")
case object Pair extends Protocol("pair")
