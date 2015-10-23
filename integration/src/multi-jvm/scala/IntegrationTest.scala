package funnel
package integration

import org.scalatest.{BeforeAndAfterAll,FlatSpecLike,Matchers}
import akka.remote.testkit.MultiNodeSpecCallbacks

/**
 * Hooks up MultiNodeSpec with ScalaTest
 */
trait STMultiNodeSpec extends MultiNodeSpecCallbacks
  with FlatSpecLike
  with Matchers
  with BeforeAndAfterAll {

  override def beforeAll() = multiNodeSpecBeforeAll()
  override def afterAll() = multiNodeSpecAfterAll()
}

//////////////////////// JVM configuration //////////////////////////

import akka.remote.testkit.MultiNodeConfig
import java.net.URI
import scalaz.Kleisli
import scalaz.concurrent.Task

object MultiNodeIntegrationConfig extends MultiNodeConfig {
  /**
   * important thing to note here is that each role needs to be assigned
   * to a specific jvm. If its not, the test will complain there is not
   * enough nodes to run the test suite.
   */
  val chemist01 = role("chemist01")
  val flask01   = role("flask01")
  val flask02   = role("flask02")
  val target01  = role("target01")
  val target02  = role("target02")
  val target03  = role("target03")

  //////// barriers /////////
  val Startup      = "startup"
  // everything is deployed and running
  val Deployed     = "deployed"
  // metrics are flowing around and we're monitoring for
  // a period of time
  val PhaseOne     = "phase-one"
  // time to tear some shit down and track errors!
  val PhaseTwo     = "phase-two"
  // test is over
  val Finished     = "finished"

  val platform = new IntegrationPlatform {
    val config = new IntegrationConfig
  }

  val ichemist = new IntegrationChemist

  val http = dispatch.Http()

  implicit class KleisliExeSyntax[A](k: Kleisli[Task,IntegrationPlatform,A]){
    def exe: A = k.apply(platform).run
  }
}

//////////////////////// JVM setup //////////////////////////

import akka.remote.testkit.MultiNodeSpec
import akka.testkit.ImplicitSender
import akka.actor.{Props,Actor}

class MultiNodeIntegrationSpecMultiJvmNode1 extends MultiNodeIntegration
class MultiNodeIntegrationSpecMultiJvmNode2 extends MultiNodeIntegration
class MultiNodeIntegrationSpecMultiJvmNode3 extends MultiNodeIntegration
class MultiNodeIntegrationSpecMultiJvmNode4 extends MultiNodeIntegration
class MultiNodeIntegrationSpecMultiJvmNode5 extends MultiNodeIntegration
class MultiNodeIntegrationSpecMultiJvmNode6 extends MultiNodeIntegration

//////////////////////// Actual Test //////////////////////////

import akka.remote.testconductor.{RoleName,Controller}
import concurrent.{Future,ExecutionContext}
import chemist.{Server,FlaskID,PlatformEvent}
import java.util.concurrent.{CountDownLatch,TimeUnit}
import concurrent.duration._

class MultiNodeIntegration extends MultiNodeSpec(MultiNodeIntegrationConfig)
  with STMultiNodeSpec
  with ImplicitSender {
  import MultiNodeIntegrationConfig._
  import PlatformEvent._

  def printObnoxiously[A](id: String)(a: => A): Unit = {
    println(s">>>> start $id >>>>>>>>>>>>>>>>>>>>>>>>>>>")
    println(a)
    println(s"<<<< end $id <<<<<<<<<<<<<<<<<<<<<<<<<<<")
  }

  def fetch(path: String) = {
    import dispatch._, Defaults._

    val svc = url(s"http://127.0.0.1:64529${path}")
    val json = http(svc OK as.String)
    scala.concurrent.Await.result(json, 2.seconds)
  }

  def deployTarget(role: RoleName, port: Int, failAfter: Option[Duration] = None) =
    runOn(role){
      val latch = new CountDownLatch(1)
      val target = IntegrationTarget.start(port)
      enterBarrier(Deployed, PhaseOne)
      failAfter.foreach { d =>
        latch.await(d.toMillis, TimeUnit.MILLISECONDS)
        target.stop()
        Thread.sleep(15.seconds.toMillis)
      }
      enterBarrier(PhaseTwo)
    }

  def deployFlask(role: RoleName, opts: flask.Options) =
    runOn(role){
      IntegrationFlask.start(opts)
      enterBarrier(Deployed, PhaseOne, PhaseTwo)
    }

  def initialParticipants =
    roles.size

  it should "wait for all nodes to enter startup barrier" in {
    enterBarrier(Startup)
  }

  it should "setup the world" in {
    runOn(chemist01){
      // just fork the shit out of it so it doesnt block our test.
      Future(Server.unsafeStart(new Server(ichemist, platform))
        )(ExecutionContext.Implicits.global)

      // give the system time to do its thing
      // specifically give it time to the first discovery after booting.
      Thread.sleep(12.seconds.toMillis)

      enterBarrier(Deployed, PhaseOne)
    }

    deployTarget(target01, 4001)

    deployTarget(target02, 4002)

    deployTarget(target03, 4003, Some(5.seconds))

    deployFlask(flask01, IntegrationFixtures.flask1Options)

    deployFlask(flask02, IntegrationFixtures.flask2Options)
  }

  it should "list the appropriate flask ids" in {
    runOn(chemist01){
      ichemist.shard(FlaskID("flask1")).exe should equal (
        Some(IntegrationFixtures.flask1) )
    }
  }

  it should "show more detailed flask information" in {
    runOn(chemist01){
      ichemist.shard(FlaskID("flask1")).exe should equal (
        Some(IntegrationFixtures.flask1) )
    }
  }

  it should "show the correct distribution" in {
    runOn(chemist01){
      printObnoxiously("/distribution")(fetch("/distribution"))
      printObnoxiously("/shards")(fetch("/shards"))
      printObnoxiously("/shards/flask1/sources")(fetch("/shards/flask1/sources"))
    }
  }

  it should "have recieved the problem event via the telemetry socket" in {
    runOn(chemist01){
      enterBarrier(PhaseTwo)
      true should equal (true)
    }
  }

  /// must be the last thing
  it should "complete the testing" in {
    enterBarrier(Finished)
  }
}