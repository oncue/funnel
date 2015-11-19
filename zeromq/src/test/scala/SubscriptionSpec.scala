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
import org.scalatest.matchers.{Matcher,MatchResult}
import org.scalatest.{FlatSpec,Matchers,BeforeAndAfterAll}
import scalaz.concurrent.Strategy
import scalaz.stream.async
import async.mutable.Queue
import scala.concurrent.duration._
import sockets._
import Publish.transportDatapoint
import scalaz.{-\/,\/,\/-}

class SubscriptionSpec extends FlatSpec
    with Matchers
    with BeforeAndAfterAll {

  lazy val S  = async.signalOf[Boolean](true)(Strategy.Executor(Monitoring.serverPool))
  lazy val W  = 30.seconds

  lazy val U1 = new URI("tcp://127.0.0.1:6478/now/numeric")
  lazy val E1 = Endpoint.unsafeApply(publish &&& bind, U1)
  lazy val M1 = Monitoring.instance(windowSize = W)
  lazy val I1 = new Instruments(M1)

  lazy val U2 = new URI("tcp://127.0.0.1:6479/previous")
  lazy val E2 = Endpoint.unsafeApply(publish &&& bind, U2)
  lazy val M2 = Monitoring.instance(windowSize = W)
  lazy val I2 = new Instruments(M2)

  private def addInstruments(i: Instruments): Unit = {
    Clocks.instrument(i)
    JVM.instrument(i)
  }

  // mirror now to this instance
  lazy val MN = Monitoring.instance

  // mirror previous to this instance
  lazy val MP = Monitoring.instance

  override def beforeAll(){
    addInstruments(I1)
    addInstruments(I2)

    Ø.link(E1)(S)(socket =>
      Monitoring.subscribe(M1)(_ => true)
        .through(ZeroMQ.write(socket))
        .onComplete(scalaz.stream.Process.eval(stop(S)))
    ).run.runAsync(_ match {
      case -\/(err) =>
        println(s"Unable to stream monitoring events to the socket ${E1.location.uri}")
        println(s"Error was: $err")

      case \/-(win) =>
    })

    Ø.link(E2)(S)(socket =>
      Monitoring.subscribe(M2)(_ => true)
        .through(ZeroMQ.write(socket))
        .onComplete(scalaz.stream.Process.eval(stop(S)))
    ).run.runAsync(_ match {
      case -\/(err) =>
        println(s"Unable to stream monitoring events to the socket ${E2.location.uri}")
        println(s"Error was: $err")

      case \/-(win) =>
    })

    Thread.sleep(2.seconds.toMillis)

    mirror(U1, MN) //, List(nowCounter))
    mirror(U2, MP) //, List(previous))

    Thread.sleep(W.toMillis*2)
  }

  private def mirror(uri: URI, to: Monitoring): Unit =
    to.mirrorAll(Mirror.from(S)
    )(uri, Map("uri" -> uri.toString)
    ).run.runAsync(_.fold(e => Ø.log.error(
                            s"Error mirroring $uri: ${e.getMessage}"), identity))


  override def afterAll(){
    stop(S).run
  }

  private def countKeys(m: Monitoring): Int =
    m.keys.compareAndSet(identity).run.get.size

  if(Ø.isEnabled){
    "Subscription" should "discriminate" in {
      val cn = countKeys(MN)
      val cp = countKeys(MP)
      val c1 = M1.keys.get.run.size
      val c2 = M2.keys.get.run.size
      c1 should be > cn
      c2 should be > cp

//      println(s"n: $cn, p: $cp, c1: $c1, c2: $c2")

      cn should be > 0
      cp should be > 0
    }
  }
}
