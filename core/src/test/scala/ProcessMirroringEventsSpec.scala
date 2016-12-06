package funnel

import java.net.URI

import com.twitter.algebird.Group
import org.scalacheck.Arbitrary._
import org.scalacheck.Prop._
import org.scalacheck._
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.Executors

import scala.concurrent.duration._
import scalaz.Nondeterminism
import scalaz.concurrent.{Strategy, Task}
import scalaz.std.list._
import scalaz.std.tuple._
import scalaz.stream.Cause.{End, Kill, Terminated}
import scalaz.stream.Process.Halt
import scalaz.stream.{Process, Sink}
import scalaz.syntax.foldable._
import scalaz.syntax.functor._

object ProcessMirroringEventsSpec extends Properties("processMirroringEvents") {

  implicit val s = Executors.newScheduledThreadPool(4)
  private val clusterName: ClusterName = "clusterName"

  property("keys are being removed upon disconnect") = secure {
    val M = Monitoring.instance(Monitoring.defaultPool, println)
    val i = new Instruments(monitoring = M)
    val gauge = i.gauge("someGauge",0.0)
    val key = gauge.key

    //closing the signal, simulating the host dropping the connection
    M.get(key).close.unsafePerformSync

    val x = M.keySenescence(Events.every(1.milliseconds),Process.eval(Task.delay(gauge.key)))

    Thread.sleep(1000)

    x.run.unsafePerformSync
    !M.keys.get.unsafePerformSync.contains(key)
  }


  property("TCP disconnect removes host from /mirror/sources list") = secure {
    val M = Monitoring.instance(Monitoring.defaultPool, println)
    val enqueueSink: Sink[Task, Command] = M.mirroringQueue.enqueue
    val uri= URI.create("http://localhost")
    val mirror = Mirror(uri,clusterName)
    val discard = Discard(uri)
    val datapoint = new Datapoint[Any](Key[String]("key",Units.TrafficLight),"green")
    val commands: Process[Task, Command] = Process.emitAll(Seq(mirror, discard))

    //this should be commands.to(enqueueSink) but scala 2.10 didn't like it
    //val commandEnqueue = commands.to(enqueueSink)
    val commandEnqueue = commands.zipWith(enqueueSink)((o,f) => f(o)).eval

    val mockParse: URI => Process[Task, Datapoint[Any]] = 
      _ => Process.eval(Task.delay(throw new RuntimeException("boom")))

    //enqueue the commands
    commandEnqueue.run.unsafePerformSync

    //start processing commands
    Task.fork(
      M.processMirroringEvents(
        mockParse,
        nodeRetries = _ => Events.takeEvery(1.millisecond,1)
      )
    ).unsafePerformAsync(_ => ())

    //Let it run
    Thread.sleep(1000)

    //cleanup
    M.mirroringQueue.close.unsafePerformSync

    M.mirroringUrls.size == 0
  }

  property("Discard command removes host from /mirror/sources list") = secure {
    val M = Monitoring.instance(Monitoring.defaultPool, println)
    val enqueueSink: Sink[Task, Command] = M.mirroringQueue.enqueue
    val uri= URI.create("http://localhost")
    val mirror = Mirror(uri,clusterName)
    val discard = Discard(uri)
    val datapoint = new Datapoint[Any](Key[String]("key",Units.TrafficLight),"green")
    val commands: Process[Task, Command] = Process.emitAll(Seq(mirror, discard))
    val commandEnqueue = commands.zipWith(enqueueSink)((o,f) => f(o)).eval
    val mockParse: URI => Process[Task, Datapoint[Any]] = _ => scalaz.stream.io.resource(Task.delay(())
    ){ _ => Task.delay{ () }
    }(_ => Task.delay(datapoint))

    //enqueue the commands
    commandEnqueue.run.unsafePerformSync

    //This is used as a flag to cancel the processing
    val b = new java.util.concurrent.atomic.AtomicBoolean(false)

    //start processing commands
    M.processMirroringEvents(mockParse).unsafePerformAsyncInterruptibly(_ => (), b)

    Thread.sleep(1000)

    //end processing
    b.set(true)
    Thread.sleep(1000)

    //cleanup
    M.mirroringQueue.close.unsafePerformSync
    M.mirroringUrls.toMap.get(clusterName) == None
  }

 /*
   NOTE WELL
   Currently this failing test kicks off a never-ending process. 
   (The test is that the code stops said process, which fails).
   Continuous running will eventually bog down your system.
   */
  property("Disconnect Command disconnects from Host")= secure {
    val M = Monitoring.instance(Monitoring.defaultPool, println)
    val enqueueSink: Sink[Task, Command] = M.mirroringQueue.enqueue
    val uri= URI.create("http://localhost")
    val mirror = Mirror(uri,clusterName)
    val discard = Discard(uri)
    val datapoint = new Datapoint[Any](Key[String]("key",Units.TrafficLight),"green")
    val result = new java.util.concurrent.atomic.AtomicBoolean(false)
    val countdown = new java.util.concurrent.CountDownLatch(1)

    val commands1: Process[Task, Command] = Process.emitAll(Seq(mirror))
    val commands2: Process[Task, Command] = Process.emitAll(Seq(discard))

    val command1Enqueue = commands1.zipWith(enqueueSink)((o,f) => f(o)).eval
    val command2Enqueue = commands2.zipWith(enqueueSink)((o,f) => f(o)).eval

    val mockDataConnection: URI => Process[Task, Datapoint[Any]] = _ => scalaz.stream.io.resource(
      Task.delay{()}
    ){ _ =>
      Task.delay{
        result.set(true)
        countdown.countDown
        ()
      }

    }(_ => Task.delay(datapoint))

    //start processing commands
    Task.fork(M.processMirroringEvents(mockDataConnection)).unsafePerformTimed(3.seconds.toMillis).attempt.unsafePerformAsync(_ => ())

    //send enqueue commands
    command1Enqueue.run.unsafePerformSync

    Thread.sleep(1000)
    //send discard commands
    command2Enqueue.run.unsafePerformSync
    Thread.sleep(2000)

    result.get
  }

  property("link executes cleanup code on process") = secure {
    val adp = new AtomicBoolean(false)
    val S = Strategy.Executor(Monitoring.defaultPool)
    val hook = scalaz.stream.async.signalOf[Unit](())(S)
    val other: Process[Task, Unit] = scalaz.stream.io.resource(
      Task.delay(())
    )(
      _ => Task.delay{adp.set(true);()}
    )(_ => Task.delay(()))
    Task.fork(Monitoring.default.link(hook)(other).run).attempt.unsafePerformAsync(_ => ())
    Thread.sleep(1000)
    hook.close.unsafePerformSync
    Thread.sleep(1000)
    adp.get
  }
}
