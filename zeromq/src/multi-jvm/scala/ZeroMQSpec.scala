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

import java.net.URI
import scalaz.concurrent.Task
import scalaz.stream.{Channel,Process,channel}
import org.scalatest.{FlatSpec,Matchers,BeforeAndAfterAll}
import sockets._
import java.util.concurrent.atomic.AtomicLong

class SpecMultiJvmNodeA extends FlatSpec with Matchers {
  if(Ø.isEnabled){
    import common._

    val E = Endpoint.unsafeApply(pull &&& bind, Settings.uri)
    val received = new AtomicLong(0L)
    val ledger: Channel[Task, String, Unit] =
      channel.lift(_ => Task(received.incrementAndGet))

    "receiving streams" should "pull all the sent messages" in {
      Ø.link(E)(Fixtures.signal)(Ø.receive)
        .map(_.toString)
        .through(ledger)
        .run.runAsync(_ => ())

      Thread.sleep(5000) // oh. so. terrible.

      stop(Fixtures.signal).run
      // check that all the items made it here
      received.get should equal (10001l)
    }
  }
}

class SpecMultiJvmNodeB extends FlatSpec with Matchers with BeforeAndAfterAll {
  if(Ø.isEnabled){
    import common._

    implicit val B = scalaz.std.anyVal.booleanInstance.conjunction

    val E = Endpoint.unsafeApply(push &&& connect, Settings.uri)

    val seq: Seq[Array[Byte]] = for(i <- 0 to 10000) yield Fixtures.data
    val k: Seq[Boolean] = seq.map(_ => true) ++ Seq(false)
    // stupid scalac cant handle this in-line.
    val proc: Process[Task, Array[Byte]] = Process.emitAll(seq)
    val alive: Process[Task, Boolean] = Process.emitAll(k)

    "publishing streams" should "send the entire fixture set" in {
      val result: Boolean = Ø.linkP(E)(alive)(socket =>
        proc.through(Ø.write(socket))).runFoldMap(identity).run

      result should equal (true)
    }
  }
}
