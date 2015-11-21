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
package zeromq

import funnel.zeromq._
import scalaz.stream.async.signalOf
import scalaz.stream.async.mutable.Signal
import scalaz.stream.Process
import scalaz.concurrent.{ Task, Strategy }

class Proxy(I: Endpoint, O: Endpoint){
  private val alive: Signal[Boolean] = signalOf[Boolean](true)(Strategy.Executor(Monitoring.defaultPool))
  private val stream: Process[Task,Boolean] =
    Ø.link(O)(alive)(s =>
      Ø.link(I)(alive)(Ø.receive
        ).through(Ø.writeTrans(s)))

  def task: Task[Unit] = stream.run
}
