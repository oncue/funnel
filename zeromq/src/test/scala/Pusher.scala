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

import scalaz.concurrent.{Task,Strategy}
import scalaz.stream.async.signalOf
import scalaz.stream.{Process,time}
import java.net.URI

object common {
  implicit val batransport: Transportable[Array[Byte]] =
    Transportable { (ba,s) =>
      Transported(s, Schemes.unknown, Versions.unknown, None, None, ba)
    }
}

abstract class Pusher(name: String, uri: URI = Settings.uri, size: Int = 1000000) {
  def main(args: Array[String]): Unit = {
    import sockets._, common._

    Ø.log.info(s"Booting $name...")

    val E = Endpoint.unsafeApply(push &&& connect, uri)


    val seq: Seq[Array[Byte]] = for(i <- 0 to size) yield Fixtures.data
    // stupid scalac cant handle this in-line.
    val proc: Process[Task, Array[Byte]] = Process.emitAll(seq)

    Ø.link(E)(Fixtures.signal)(socket =>
      proc.through(Ø.write(socket)
        ).onComplete(Process.eval(stop(Fixtures.signal)))).run.run
  }
}

import scala.concurrent.duration._

abstract class ApplicationPusher(name: String, aliveFor: FiniteDuration = 12.seconds) {
  val S = Strategy.Executor(Monitoring.defaultPool)

  def main(args: Array[String]): Unit = {
    import instruments._

    Ø.log.info(s"Booting $name...")

    implicit val log: String => Unit = println _

    val M = Monitoring.default
    val T = counter("testing/foo")

    T.incrementBy(2)

    Publish.toUnixSocket(Settings.uri.toString, Fixtures.signal)

    time.sleep(aliveFor)(S, Monitoring.schedulingPool)
      .onComplete(Process.eval_(Fixtures.signal.get)).run.run

    Ø.log.info(s"Stopping the '$name' process...")
  }
}
