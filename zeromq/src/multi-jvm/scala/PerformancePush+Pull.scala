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

import scalaz.concurrent.Task
import scalaz.stream.{channel,Channel,Process}
import scala.concurrent.duration._
import java.net.URI

object PerfMultiJvmPusher1 extends Pusher("pusher-1")

object PerfMultiJvmPusher2 extends Pusher("pusher-2")

object PerfMultiJvmPusher3 extends Pusher("pusher-3")

object PerfMultiJvmPuller {
  import java.util.concurrent.atomic.AtomicLong
  import concurrent.duration.FiniteDuration
  import sockets._

  val received = new AtomicLong(0L)

  def main(args: Array[String]): Unit = {
    Ø.log.info(s"Booting Puller...")

    val start = System.currentTimeMillis
    val E = Endpoint.unsafeApply(pull &&& bind, Settings.uri)

    val ledger: Channel[Task, String, Unit] = channel.lift(
      _ => Task {
        val i = received.incrementAndGet
        val time = FiniteDuration(System.currentTimeMillis - start, "milliseconds").toSeconds
        if(i % 10000 == 0) println(s"Pulled $i values in $time seconds.") // print it out every 1k increment
        else ()
      }
    )

    Ø.link(E)(Fixtures.signal)(Ø.receive)
      .map(_.toString)
      .through(ledger)
      .run.runAsync(_ => ())

    // just stupidly wait around in this thread until the ledger says
    // we've recieved the expected amount of items from the pushers,
    // then continue on to dump the mini report. Never do this in your
    // mainline code!
    while(received.get < 2999999){ }

    val finish = System.currentTimeMillis

    Ø.log.debug("Puller - Stopping the task...")

    val seconds = FiniteDuration(finish - start, "milliseconds").toSeconds
    val bytes = Fixtures.data.length * received.get
    val megabits = bytes.toDouble / Fixtures.megabitInBytes

    Ø.log.info("=================================================")
    Ø.log.info(s"duration  = $seconds seconds")
    Ø.log.info(s"msg/sec   = ${received.get.toDouble / seconds}")
    Ø.log.info(s"megabits  = $megabits")
    Ø.log.info(s"data mb/s = ${megabits.toDouble / seconds}")
    Ø.log.info("=================================================")


  }
}
