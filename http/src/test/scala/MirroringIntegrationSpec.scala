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
package http

import java.net.URI
import scala.concurrent.duration._
import scalaz.concurrent.Strategy
import scalaz.stream.Process
import scalaz.stream.async.mutable.Queue
import scalaz.stream.async
import org.scalatest._, matchers.ShouldMatchers

class MirroringIntegrationSpec extends FlatSpec with Matchers with BeforeAndAfterAll {

  private def addInstruments(i: Instruments): Unit = {
    Clocks.instrument(i)
    JVM.instrument(i)
  }

  private def mirrorFrom(port: Int): Unit =
    MI.mirrorAll(SSE.readEvents(_))(
      new URI(s"http://localhost:$port/stream/previous"),
      Map("port" -> port.toString)
    ).run.runAsync(_ => ())

  implicit val log = (s: String) => { println(s) }

  val W = 30.seconds

  lazy val M1 = Monitoring.instance(windowSize = W)
  lazy val I1 = new Instruments(M1)

  lazy val M2 = Monitoring.instance(windowSize = W)
  lazy val I2 = new Instruments(M2)

  lazy val M3 = Monitoring.instance(windowSize = W)
  lazy val I3 = new Instruments(M3)

  val MS1 = MonitoringServer.start(M1, 3001)
  val MS2 = MonitoringServer.start(M2, 3002)
  val MS3 = MonitoringServer.start(M3, 3003)

  // mirror to this instance
  lazy val MI = Monitoring.instance

  val KMI = MonitoringServer.start(MI, 5775)

  override def beforeAll(){
    addInstruments(I1)
    addInstruments(I2)
    addInstruments(I3)

    Thread.sleep(2000)

    // shutdown all the servers
    mirrorFrom(3001)
    mirrorFrom(3002)
    mirrorFrom(3003)

    Thread.sleep(W.toMillis * 2)
  }

  override def afterAll(){
    // shutdown all the servers
    MS1.stop()
    MS2.stop()
    MS3.stop()
    KMI.stop()
  }

  // never do stuff like this in production code.
  // this is a dirty hack to make some assertions about
  // the monitoring instances
  private def countKeys(m: Monitoring): Int =
    m.keys.compareAndSet(identity).run.get.filter(_.startsWith("previous")).size

  it should "be able to mirror all the keys from the other instances into the current state of 'MI'" in {
    (countKeys(M1) +
    countKeys(M2) +
    countKeys(M3)) should equal (MI.keys.compareAndSet(identity).run.get.size)
  }
}
