package funnel
package riemann

import com.aphyr.riemann.client.RiemannClient
import com.aphyr.riemann.Proto.{Event => REvent}
import Events.Event
import scala.concurrent.duration._
import scalaz.\/
import scalaz.concurrent.{Strategy,Task,Actor}
import scalaz.stream.{async,Process,time}
import scala.collection.JavaConverters._
import scalaz.stream.async.mutable.Signal
import journal.Logger

object Riemann {
  val log = Logger[this.type]

  sealed trait Pusher
  final case class Hold(e: REvent) extends Pusher
  final case object Flush extends Pusher

  @volatile private var store: List[REvent] = Nil

  private[riemann] def collector(
    R: RiemannClient
  ): Actor[Pusher] = {
    implicit val S = Strategy.Executor(Monitoring.serverPool)
    implicit val P = Monitoring.schedulingPool

    val a = Actor.actor[Pusher] {
      case Hold(e) => store = (e :: store)
      case Flush   => {
        R.sendEvents(store.asJava)
        log.debug(s"successfully sent batch of ${store.length}")
        store = Nil
      }
    }(S)

    time.awakeEvery(1.minute)(S, P).evalMap {_ =>
      Task(a(Flush))
    }.run.unsafePerformAsync(_ => ())

    a
  }

  private def splitStats(key: Key[Any], s: Stats): List[Datapoint[Any]] = {
    List(
      Datapoint(key.modifyName(_ + "/count"), s.count.toDouble),
      Datapoint(key.modifyName(_ + "/variance"), s.variance),
      Datapoint(key.modifyName(_ + "/mean"), s.mean),
      Datapoint(key.modifyName(_ + "/last"), s.last.getOrElse(Double.NaN)),
      Datapoint(key.modifyName(_ + "/standardDeviation"), s.standardDeviation)
    )
  }

  private def trafficLightToRiemannState(dp: Datapoint[Any]): Datapoint[Any] =
    if(dp.units == Units.TrafficLight)
      dp.value match {
        case TrafficLight.Red     => Datapoint(dp.key, "critical")
        case TrafficLight.Yellow  => Datapoint(dp.key, "warning")
        case TrafficLight.Green   => Datapoint(dp.key, "ok")
      }
    else dp

  private def tags(s: String) = s.split("/")

  /**
    * This whole method is an imperitive getho. However, we use the
    * Riemann java client DSL, and make "use" of the fact that the underlying
    * objects are actually affecting the protocol buffers wire format.
    *
    * This method really needs to die, but unless we go to the trouble of
    * implementing our own client, that's not going to happen as there has to
    * be some plumbing to mediate from our world to the riemann world.
    *
    * C'est la vie!
    */
  private def toEvent(c: RiemannClient, ttl: Float)(pt: Datapoint[Any]
    ): REvent = {
    val name = pt.key.name
    val host = pt.key.attributes.get("url")

    val t = tags(name)

    val e = c.event
             .tags(t: _*)
             .description(s"${pt.key.typeOf} ${pt.key.units}")
             .time(System.currentTimeMillis / 1000L)
             .ttl(ttl)
             .attribute("service-revision", t.headOption.getOrElse("unknown"))

    val _1 = e.service(name)
    host.foreach { h =>
      e.host(h)
    }

    val _2 = pt.value match {
      case a: Double => e.metric(a)
      case a: String => e.state(trafficLightToRiemannState(pt).value.toString)
      case b: Boolean => e.state(b.toString)
      // will *never* be encountered at this point
      // case s: Stats =>
      case x => log.debug(s"unknown datapoint value class of type: ${x.getClass.getName}"); ???
    }

    // lifts the EventDSL into an REvent
    e.build()
  }

  private def liftDatapointToStream(dp: Datapoint[Any]): Process[Task, Datapoint[Any]] =
    dp.value match {
      case s: Stats => Process.emitAll(splitStats(dp.key, s))
      case _        => Process.emit(dp)
    }

  /**
   * Publish all datapoints from this `Monitoring` to the given
   * `RiemannClient`. Uses `retries` to control the reconnect frequency
   * if `RiemannClient` is unavailable. This returns a blocking `Task`.
   */
  def publish(
    M: Monitoring,
    ttlInSeconds: Float = 20f
  )(c: RiemannClient, a: Actor[Pusher]
  ): Task[Unit] = {
    Monitoring.subscribe(M)(_ => true).flatMap(liftDatapointToStream
      ).zipWithIndex.evalMap { case (pt,i) =>
        Task {
          a ! Hold(toEvent(c, ttlInSeconds)(pt))
          if(i % 1000 == 0)
            a ! Flush
          else ()
        }(Monitoring.defaultPool)
      // )
    }.run
  }

  /**
   * Publish the values from the given monitoring instance to riemann in batches.
   */
  def publishToRiemann[A](
    M: Monitoring,
    ttlInSeconds: Float = 20f
  )(riemannClient: RiemannClient,
    riemannName: String)(
    myName: String = "Funnel Mirror"
  ): Task[Unit] = {

    val actor = collector(riemannClient)

    publish(M, ttlInSeconds)(riemannClient, actor)
  }
}