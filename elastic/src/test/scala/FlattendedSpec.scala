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
package elastic

import java.io.IOException
import java.util.concurrent.{ThreadFactory, Executors, ExecutorService}
import org.scalatest.{FlatSpec,Matchers}
import org.scalatest.concurrent._
import org.scalatest.time._
import scala.collection.mutable
import scala.concurrent.duration._
import scala.language.postfixOps
import scalaz.Kleisli
import scalaz.concurrent.Task
import scalaz.stream.Process

class FlattenedSpec extends FlatSpec with Matchers with Eventually {
  val F = ElasticFlattened(Monitoring.default, new Instruments(Monitoring.default))

  def point =
    Datapoint[Any](Key[Double](s"now/${java.util.UUID.randomUUID.toString}", Units.Count, "some description",
      Map(AttributeKeys.source -> "http://10.0.10.10/stream/now", AttributeKeys.kind -> "gauge")
    ), 3.14)

  def point2 =
    Datapoint[Any](Key[Stats](s"sliding/${java.util.UUID.randomUUID.toString}", Units.Count, "some description",
      Map(AttributeKeys.source -> "http://10.0.10.10/stream/now", AttributeKeys.kind -> "gauge")
    ), Stats(3.14))

  def point3 =
    Datapoint[Any](Key[Stats](s"sliding/file_system.%2F.use_percent", Units.Bytes(Units.Base.Kilo), "some description",
      Map(AttributeKeys.source -> "http://10.0.10.10/stream/now", AttributeKeys.kind -> "gauge")
    ), Stats(3.14))


  //use batch size one to be able to predict how many http requests we will see
  val sharedCfg = new ElasticCfg("http://some-host", "idx", "a", "yyyy.MM.ww", "template",
    Some("version.sbt"),
    http = null,
    List("previous"),
    subscriptionTimeout = 300.millis, bufferSize = 4096, batchSize = 1)

  case class MonitoringEnv(M: Monitoring, I: Instruments, EF: ElasticFlattened, H: InMemoryHttpLayer) {
    private def taskRun[A](t: Duration)(op: Task[A]): java.util.concurrent.atomic.AtomicBoolean = {
      val b = new java.util.concurrent.atomic.AtomicBoolean(false)
      op.unsafePerformAsyncInterruptibly(t => (), b)
      b
    }

    private def processRun[A](t: Duration)(p: Process[Task, A]): java.util.concurrent.atomic.AtomicBoolean = {
      val b = new java.util.concurrent.atomic.AtomicBoolean(false)
      p.run.unsafePerformAsyncInterruptibly(_ => (), b)
      b
    }

    // starts publishing process and returns function to stop it
    def publish(): () => Unit = {
      val s1 = taskRun(10.seconds)(EF.publish("env", "a1", "a2")(sharedCfg))
      val s2 = processRun(10.seconds)(M.dataDislodgement)

      () => {
        s1.set(true)
        s2.set(true)
        ()
      }
    }
  }

  private def daemonThreads(name: String) = new ThreadFactory {
    def newThread(r: Runnable) = {
      val t = Executors.defaultThreadFactory.newThread(r)
      t.setDaemon(true)
      t.setName(name)
      t
    }
  }

  def monitoringEnv(rules: Seq[Rule] = Rule.defaultRules(sharedCfg))(testCode: (MonitoringEnv) => Any): Unit =
    monitoringEnv(InMemoryHttpLayer(rules))(testCode)

  def monitoringEnv(httpLayer: InMemoryHttpLayer)(testCode: (MonitoringEnv) => Any): Unit = {
    val pool: ExecutorService = Executors.newFixedThreadPool(8, daemonThreads("test-threads"))

    val M = Monitoring.instance(ES = pool, windowSize = 50.millis)

    val instruments = new Instruments(M, bufferTime = 20 millis)
    val ef = ElasticFlattened(M, instruments, httpLayer)

    val env = MonitoringEnv(M, instruments, ef, httpLayer)

    try {
      testCode(env)
    } finally {
      pool.shutdownNow()
    }
  }

  "toJson" should "correctly render documents to json in the happy case" in {
    println {
      F.toJson("dev", "127.0.0.1", "local")(point3)
    }
  }


  "template provisioning" should "provision template if missing" in
    monitoringEnv(Seq(Rule.failedPUT(s"/_template/${sharedCfg.templateName}", 999))) {
      (env: MonitoringEnv) =>
        val thrown:HttpException = the [HttpException] thrownBy env.EF.publish("env", "a1", "a2")(sharedCfg).unsafePerformSync
        thrown.code should equal (999)
  }

  it should "not try to provision template otherwise" in monitoringEnv() {
    (env: MonitoringEnv) =>
      //wait until we see first metric, by that time all template check logic should be completed

      //send test metric
      val c = env.I.counter("m/test")
      c.increment

      val stopPublish = env.publish()

      eventually (timeout(Span(5, Seconds))) { env.H.requests.find(r => r.method == "POST") shouldBe defined }

      stopPublish()

      env.H.requests.find(r => r.method == "HEAD") shouldBe defined //expect lookup for template but no attempt to overwrite
      env.H.requests.find(r => r.method == "PUT") shouldBe empty
  }

  "publish" should "post first document" in monitoringEnv() {
    (env: MonitoringEnv) =>
      val c = env.I.counter("m/test")
      c.increment

      val stopPublish = env.publish()

      eventually (timeout(Span(5, Seconds))) {
        env.H.requests.find(r => r.method == "POST" && r.contains("m.test")) shouldBe defined
      }

      stopPublish()
  }

  it should "continue posting documents periodically" in monitoringEnv() {
    (env: MonitoringEnv) =>
      val c = env.I.counter("m/test")
      c.increment

      val stopPublish = env.publish()

      //update metric few times (waiting for longer than buffer window) => expect to see documents emitted
      (1 to 3) foreach {
        i =>
          Thread.sleep(100)
          c.increment
      }

      eventually (timeout(Span(5, Seconds))) {
        env.H.requests.count(r => r.method == "POST" && r.contains("m.test")) should be > 3
      }

      stopPublish()
  }

  //simulating errors on some requests
  private def failingHttpLayer(t: Throwable):InMemoryHttpLayer = new InMemoryHttpLayer(mutable.ListBuffer(Rule.defaultRules(sharedCfg): _*)) {
    var cnt = 0

    override def http(v: HttpOp): Elastic.ES[String] = {
      cnt = cnt + 1
      cnt match {
        case 4 =>
          Kleisli.kleisli[Task, ElasticCfg, String]((cfg: ElasticCfg) => Task.fail(t))
        case _ => super.http(v)
      }
    }
  }

  it should "not stop after http error" in monitoringEnv(failingHttpLayer(HttpException(500))) {
    (env: MonitoringEnv) =>
      val c = env.I.counter("m/test")
      c.increment

      val stopPublish = env.publish()

      //update metric few times (waiting for longer than buffer window) => expect to see documents emitted
      (1 to 10) foreach {
        i =>
          Thread.sleep(100)
          c.increment
      }

      eventually (timeout(Span(5, Seconds))) {
        env.H.requests.count(r => r.method == "POST" && r.contains("m.test")) should be > 9
      }

      stopPublish()
  }

  it should "retry and proceed after non http error" in monitoringEnv(failingHttpLayer(new IOException("TestException"))) {
    (env: MonitoringEnv) =>
      val c = env.I.counter("m/test")
      c.increment

      val stopPublish = env.publish()

      //update metric few times (waiting for longer than buffer window) => expect to see documents emitted
      (1 to 10) foreach {
        i =>
          Thread.sleep(100)
          c.increment
      }

      eventually (timeout(Span(5, Seconds))) {
        env.H.requests.count(r => r.method == "POST" && r.contains("m.test")) should be > 10
      }

      stopPublish()
  }
}
