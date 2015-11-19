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

import funnel._, zeromq._
import scalaz.concurrent.{Task,Strategy}
import scalaz.stream.Process
import scala.concurrent.duration._
import java.net.URI

object ExampleMultiJvmPusher1 extends ApplicationPusher("push-1")

object ExampleMultiJvmPuller {
  import scalaz.stream.io
  import scalaz.stream.Channel
  import java.util.concurrent.atomic.AtomicLong
  import sockets._

  val received = new AtomicLong(0L)

  def main(args: Array[String]): Unit = {
    val start = System.currentTimeMillis

    val E = Endpoint.unsafeApply(pull &&& bind, Settings.uri)

    Ø.link(E)(Fixtures.signal)(Ø.receive)
      .map(_.toString)
      .to(io.stdOut)
      .run.runAsync(_ => ())

    Thread.sleep(10.seconds.toMillis)

    stop(Fixtures.signal).run

    Ø.log.info("Stopping the pulling process...")
  }
}

