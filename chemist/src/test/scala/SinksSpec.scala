//: ----------------------------------------------------------------------------
//: Copyright (C) 2016 Verizon.  All Rights Reserved.
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

import funnel.chemist.FlaskCommand.{Monitor, Unmonitor}
import funnel.chemist._
import org.scalatest.{FlatSpec, Matchers}
import java.net.URI
import java.util.concurrent.Executors

import funnel.chemist.Chemist.Context
import funnel.chemist.Sharding._

import scalaz.{==>>, Catchable, Monad}
import scalaz.concurrent._
import scalaz.stream.async._
import scalaz.stream._

class SinksSpec extends FlatSpec with Matchers {
  case class UnstableFlask(failForFlask: String) extends RemoteFlask {
    var commandsCompleted = 0
    var commandsFailed = 0

    def command(c: FlaskCommand): Task[Unit] =
      c match {
        case Monitor(f, targets) if targets.exists(_.cluster.startsWith(failForFlask)) =>
          Task.delay { commandsFailed +=1; throw new RuntimeException("Bad flask!") }
        case Unmonitor(f, targets) if targets.exists(_.cluster.startsWith(failForFlask)) =>
          Task.delay { commandsFailed +=1; throw new RuntimeException("Bad flask!") }
        case _ => Task.delay { commandsCompleted +=1; () }
      }
  }

  implicit val scheduler = Executors.newScheduledThreadPool(1)

  val localhost: Location =
    Location(
      host = "127.0.0.1",
      port = 5775,
      datacenter = "local",
      protocol = NetworkScheme.Http,
      intent = LocationIntent.Mirroring,
      templates = Seq.empty)

  def fakeFlask(id: String) = Flask(FlaskID(id), localhost)

  val broken: Distribution = ==>>(
    (fakeFlask("a"), Set(Target("bad", new URI("http://localhost:1234")))),
    (fakeFlask("b"), Set(Target("good", new URI("http://localhost:1234"))))
  )

  val valid: Distribution = ==>>(
    (fakeFlask("c"), Set(Target("good1", new URI("http://localhost:1234")))),
    (fakeFlask("d"), Set(Target("good2", new URI("http://localhost:1234"))))
  )

  val q = boundedQueue[PlatformEvent](500)

  "unsafeNetworkIO" should "issue commands for valid distributions" in {
    val f = UnstableFlask("bad")

    //perform two distributions => expect 6 commands total and no errors
    Process(
      Context[Plan](Distribution.empty, Distribute(valid)),
      Context[Plan](Distribution.empty, Redistribute(valid, valid))
    ).toSource.to(sinks.unsafeNetworkIO(f, q)).run.unsafePerformSync

    f.commandsCompleted shouldBe 6
    f.commandsFailed shouldBe 0
  }

  it should "survive errors during distribution" in {
    val f = UnstableFlask("bad")

    //perform two distributions, first includes "bad" flask
    // we expect that
    //   1) process will not fail
    //   2) even when one flask fails it will not prevent distributing work to live flasks
    Process.apply(
      Context[Plan](Distribution.empty, Distribute(broken)),
      Context[Plan](Distribution.empty, Distribute(valid))
    ).toSource.to(sinks.unsafeNetworkIO(f, q)).run.unsafePerformSync

    f.commandsCompleted shouldBe 3
    f.commandsFailed shouldBe 1
  }

  it should "survive errors on attempt to stop (redistribution)" in {
    val f = UnstableFlask("bad")

    //perform two distributions, every time one command will fail
    // we expect that
    //   1) process will not fail
    //   2) at most one command will succeed for "redistribute" step
    //       (because "unmonitor" commands will run in parallel)
    Process(
      Context[Plan](Distribution.empty, Redistribute(broken, valid)),
      Context[Plan](Distribution.empty, Distribute(valid))
    ).toSource.to(sinks.unsafeNetworkIO(f, q)).run.unsafePerformSync

    f.commandsCompleted should be >= 2
    f.commandsCompleted should be <= 3
    f.commandsFailed shouldBe 1 //we should not try to run anything after failure
  }

  it should "survive errors on attempt to start (redistribution)" in {
    val f = UnstableFlask("bad")

    //perform two distributions, every time one command will fail
    // we expect that
    //   1) process will not fail
    //   2) "stop" part of redistribute step will succeed
    //   3) "start" part of redistribute step will succeed for good flasks
    Process.apply(
      Context[Plan](Distribution.empty, Redistribute(valid, broken)),
      Context[Plan](Distribution.empty, Distribute(valid))
    ).toSource.to(sinks.unsafeNetworkIO(f, q)).run.unsafePerformSync

    f.commandsCompleted shouldBe 5
    f.commandsFailed shouldBe 1 //we should not try to run anything after failure
  }

  it should "not issue commands if there is no work to do" in {
    val f = UnstableFlask("bad")

    // if we get plans with no actionable items then we should not call flask
    Process.apply(
      Context[Plan](Distribution.empty, Redistribute(Distribution.empty, Distribution.empty)),
      Context[Plan](Distribution.empty, Distribute(Distribution.empty))
    ).toSource.to(sinks.unsafeNetworkIO(f, q)).run.unsafePerformSync

    f.commandsCompleted shouldBe 0
    f.commandsFailed shouldBe 0

  }
}
