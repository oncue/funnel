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

import funnel.zeromq._, sockets._
import scalaz.concurrent.{Task,Strategy}
import scalaz.stream.{Process,time}
import scala.concurrent.duration._

object TestingMultiJvmPusher1 extends ApplicationPusher("push-1")

object TestingMultiJvmPusher2 extends ApplicationPusher("push-2")

object TestingMultiJvmPublisher {
  def main(args: Array[String]): Unit = {

    val (i,o) = (for {
      a <- Endpoint(pull &&& bind, Settings.uri)
      b <- Endpoint(publish &&& bind, Settings.tcp)
    } yield (a,b)).getOrElse(sys.error("Unable to configure the endpoints for the agent."))

    new zeromq.Proxy(i,o).task.run
  }
}

object TestingMultiJvmSubscriber {
  import scalaz.stream.io
  import scalaz.stream.async.signalOf

  val S = Strategy.Executor(Monitoring.defaultPool)

  def main(args: Array[String]): Unit = {
    val E = Endpoint(subscribe &&& (connect ~ topics.all), Settings.tcp
      ).getOrElse(sys.error("Unable to configure the TCP subscriber endpoint"))

    Ø.link(E)(Fixtures.signal)(Ø.receive).map(t => new String(t.bytes)).to(io.stdOut).run.runAsync(_ => ())

    time.sleep(10.seconds)(S, Monitoring.schedulingPool)
      .onComplete(Process.eval_(Fixtures.signal.get)).run.run

    println("Subscriber - Stopping the task...")
  }
}
