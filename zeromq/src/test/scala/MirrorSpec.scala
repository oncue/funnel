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
import scalaz.concurrent.Strategy
import scalaz.concurrent.Task
import scalaz.stream.{Channel,Process,io,async}
import scalaz.stream.async.mutable.Queue
import org.scalatest.{FlatSpec,Matchers,BeforeAndAfterAll}
import sockets._
import scala.concurrent.duration._

class MirrorSpec extends FlatSpec with Matchers with BeforeAndAfterAll {

  lazy val S  = async.signalOf[Boolean](true)(Strategy.Executor(Monitoring.serverPool))
  lazy val W  = 20.seconds

  lazy val U1 = new URI("zeromq+tcp://127.0.0.1:4578/previous")
  lazy val E1 = Endpoint.unsafeApply(publish &&& bind, U1)
  lazy val M1 = Monitoring.instance(windowSize = W)
  lazy val I1 = new Instruments(M1)

  lazy val U2 = new URI("zeromq+tcp://127.0.0.1:4579/previous")
  lazy val E2 = Endpoint.unsafeApply(publish &&& bind, U2)
  lazy val M2 = Monitoring.instance(windowSize = W)
  lazy val I2 = new Instruments(M2)

  // mirror to this instance
  lazy val MI = Monitoring.instance(windowSize = W)

  private def addInstruments(i: Instruments): Unit = {
    Clocks.instrument(i)
    JVM.instrument(i)
  }

  private def countKeys(m: Monitoring): Int =
    m.keys.compareAndSet(identity).run.get.filter(_.startsWith("previous")).size

  private def mirrorFrom(uri: URI): Unit =
    MI.mirrorAll(Mirror.from(S)
      )(uri, Map("uri" -> uri.toString)
      ).run.runAsync(_.fold(e => Ø.log.error(
        s"Error mirroring $uri: ${e.getMessage}"), identity))

  override def beforeAll(){
    if(Ø.isEnabled){
      addInstruments(I1)
      addInstruments(I2)

      Publish.to(E1)(S,M1)
      Publish.to(E2)(S,M2)

      Thread.sleep(2.seconds.toMillis)

      mirrorFrom(U1)
      mirrorFrom(U2)

      Thread.sleep(W.toMillis*2)
    }
  }

  override def afterAll(){
    stop(S).run
  }

  if(Ø.isEnabled){
    "zeromq mirroring" should "pull values from the specified monitoring instance" in {
      (countKeys(M1) + countKeys(M2)) should equal (MI.keys.get.run.size)
      MI.keys.get.run.size should be > 0
    }
  }
}
