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

import scalaz.concurrent.Strategy
import scalaz.{\/,-\/,\/-}
import scalaz.syntax.traverse._
import scalaz.syntax.monad._
import scalaz.concurrent.{Actor, Task}
import scalaz.stream.{Process, Process0, Process1, Sink, time}
import scalaz.stream.async.mutable.Signal
import com.amazonaws.services.sqs.model.Message
import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.autoscaling.AmazonAutoScaling
import funnel.aws._
import java.net.URI
import scala.concurrent.duration._

/**
 * The purpose of this object is to manage all the "lifecycle" events
 * associated with subordinate Flask instances. Whenever they start,
 * stop, etc the SQS event will come in and be presented on the stream.
 *
 * The design here is that incoming events are translated into a lifecycle
 * algebra which is then acted upon. This is essentially interpreter pattern.
 */
object Lifecycle {
  import JSON._
  import argonaut._, Argonaut._
  import journal.Logger
  import scala.collection.JavaConverters._
  import metrics._
  import PlatformEvent._

  private val log = Logger[Lifecycle.type]
  private val noop = \/-(Seq(NoOp))

  /**
   * Attempt to parse incomming SQS messages into `AutoScalingEvent`. Yield a
   * `MessageParseException` in the event the message is not decipherable.
   */
  def parseMessage(msg: Message): Throwable \/ AutoScalingEvent =
    Parse.decodeEither[AutoScalingEvent](msg.getBody).leftMap(MessageParseException(_))

  private val defaultTicker: Process[Task,Duration] =
    time.awakeEvery(12.seconds)(Strategy.Executor(Chemist.defaultPool), Chemist.schedulingPool)

  /**
   * This function essentially converts the polling of an SQS queue into a scalaz-stream
   * process. Said process contains Strings being delivered on the SQS queue, which we
   * then attempt to parse into an `AutoScalingEvent`. Assuming the message parses correctly
   * we then pass the `AutoScalingEvent` to the `interpreter` function for further processing.
   * In the event the message does not parse to an `AutoScalingEvent`,
   */
  def stream[A](queueName: String, ticker: Process[Task,A] = defaultTicker
    )(sqs: AmazonSQS, asg: AmazonAutoScaling, ec2: AmazonEC2, dsc: Discovery
    ): Process[Task, PlatformEvent] = {
      // adding this function to ensure that parse errors do not get
      // lifted into errors that will later fail the stream, and that
      // any errors in the interpreter are properly handled.
      def go(m: Message): Task[Throwable \/ Seq[PlatformEvent]] =
        parseMessage(m).traverseU(interpreter(_)(asg, ec2, dsc)).handle {
          case MessageParseException(err) =>
            log.warn(s"Unexpected recoverable error when parsing lifecycle message: $err")
            noop

          case InstanceNotFoundException(id,kind) =>
            log.warn(s"Unexpected recoverable error locating $kind id '$id' specified on lifecycle message.")
            noop

          case e =>
            log.warn(s"Failed to handle error state when recieving lifecycle event. event = ${m.getBody}, error = $e")
            e.printStackTrace
            noop
        }

    for {
      a <- SQS.subscribe(queueName, ticker = ticker)(sqs)(Chemist.defaultPool)
      // _ <- Process.eval(Task(log.debug(s"stream, number messages recieved: ${a.length}")))
      b <- Process.emitAll(a)
      _ <- Process.eval(Task(log.debug(s"stream, raw message recieved: $b")))
      c <- Process.eval(go(b)).stripW
      d <- Process.emitAll(c)
      _ <- Process.eval(Task(log.debug(s"stream, computed action: $c")))
      _ <- SQS.deleteMessages(queueName, a)(sqs)
    } yield d
  }

  def interpreter(e: AutoScalingEvent
    )(asg: AmazonAutoScaling, ec2: AmazonEC2, dsc: Discovery
    ): Task[Seq[PlatformEvent]] = {

    log.debug(s"interpreting event: $e")

    // MOAR side-effects!
    LifecycleEvents.increment

    def targetsFromId(id: TargetID): Task[Seq[NewTarget]] =
      for {
        i <- dsc.lookupTargets(id)
      _  = log.debug(s"Found instance metadata from remote: $i")
      } yield i.toSeq.map(NewTarget)

    def terminatedTargetsFromId(id: TargetID): Task[Seq[PlatformEvent]] =
      for {
        i <- dsc.lookupTargets(id)
      } yield i.toSeq.map(t => TerminatedTarget(t.uri))

    def isFlask: Task[Boolean] = {
      (for {
        n <- e.metadata.get("asg-name").map(Task.now).getOrElse(Task.fail(NotAFlaskException(e)))
        a <- ASG.lookupByName(n)(asg)
        _  = log.debug(s"Lifecycle.isFlask: found ASG from the EC2 lookup: $a")
        r <- a.getTags.asScala.find(t =>
               t.getKey.trim == AwsTagKeys.name &&
               t.getValue.trim.startsWith("flask")
             ).fold(Task.now(false))(_ => Task.now(true))
        _  = log.debug(s"Lifecycle.isFlask, r = $r")
      } yield r).or(Task.now(false))
    }

    def newFlask(id: FlaskID): Task[Seq[PlatformEvent]] =
      for {
        flask <- dsc.lookupFlask(id)
      } yield Seq(NewFlask(flask))

    e match {
      case AutoScalingEvent(_,Launch,_,_,_,id,_) =>
        isFlask.ifM(newFlask(FlaskID(id)), targetsFromId(TargetID(id)))

      case AutoScalingEvent(_,Terminate,_,_,_,id,_) =>
        isFlask.ifM(Task.now(Seq(TerminatedFlask(FlaskID(id)))), terminatedTargetsFromId(TargetID(id)))

      case _ => Task.now(Seq(NoOp))
    }
  }

  def logErrors(x: Throwable \/ Seq[PlatformEvent]): Process[Task,PlatformEvent] = x match {
    case -\/(t) =>
      log.error(s"Problem encountered when trying to processes SQS lifecycle message: $t")
      t.printStackTrace
      Process.halt
    case \/-(a) => Process.emitAll(a)
  }

}
