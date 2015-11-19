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
package chemist
package aws

import org.scalatest.{FlatSpec,Matchers}
import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.autoscaling.AmazonAutoScaling
import scalaz.concurrent.Strategy
import scalaz.{\/,\/-,-\/,==>>}
import scalaz.stream.{Process,Sink}
import scalaz.concurrent.Task
import scalaz.stream.async.signalOf
import scalaz.stream.async.mutable.Signal
import scalaz.std.string._
import Sharding.Distribution

class LifecycleSpec extends FlatSpec with Matchers {
  import PlatformEvent._

  val ec2 = TestAmazonEC2(Fixtures.instances)
  val uuid = java.util.UUID.randomUUID.toString
  val sqs1 = TestAmazonSQS(Fixtures.asgEvent(Launch, name = uuid, instanceId = "i-flaskAAA"))
  val sqs2 = TestAmazonSQS(Fixtures.asgEvent(Terminate, name = uuid, instanceId = "i-flaskAAA"))
  val sqs3 = TestAmazonSQS("invalid-message")
  val templates = List(LocationTemplate("http://@host:@port/stream/previous"))

  val asg1 = TestAmazonASG.single(_ => uuid)

  val dsc = new AwsDiscovery(ec2, asg1, DefaultClassifier, templates)

  val k1 = "i-dx947af7"
  val k2 = "i-15807647"

  private def fromStream(sqs: AmazonSQS, asg: AmazonAutoScaling): Option[PlatformEvent] =
    Lifecycle.stream("name-of-queue", Process.emit(1))(sqs, asg, ec2, dsc
      ).runLast.run // never do this anywhere but tests

  behavior of "the stream"

  it should "produce the right platform even when a new flask is launched" in {
    val NewFlask(f) = fromStream(sqs1, asg1).get
    f.id.value should equal( "i-flaskAAA")
  }

  it should "produce the right platform event when a flask is terminated" in {
    fromStream(sqs2, asg1).get should equal (TerminatedFlask(FlaskID("i-flaskAAA")))
  }

  it should "produce a parse exception in the event the message on SQS cannot be parsed" in {
    fromStream(sqs3, asg1) should equal (None)
  }

  behavior of "Lifecycle.interpreter"

  import scalaz.syntax.traverse._
  import scalaz.{Unapply,Traverse}
  import scalaz.syntax.either._

  def check(json: String): Task[Throwable \/ Seq[PlatformEvent]] =
    Lifecycle.parseMessage(TestMessage(json)
      ).traverse(Lifecycle.interpreter(_)(asg1, ec2, dsc))

  it should "parse messages and produce the right action" in {
    val \/-(Seq(NewFlask(f))) = check(Fixtures.asgEvent(Launch, instanceId = "i-flaskAAA")).run
    f.id.value should equal("i-flaskAAA")
    check("INVALID-MESSAGE").map(_ => true).run should equal (true)
  }
}
