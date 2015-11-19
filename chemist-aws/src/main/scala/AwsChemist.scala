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

import journal.Logger
import scalaz.syntax.kleisli._
import scalaz.concurrent.{Task,Strategy}
import scalaz.stream.async.boundedQueue
import funnel.aws.{SNS,CFN}

class AwsChemist[A <: Aws] extends Chemist[A]{
  private val log = Logger[AwsChemist[_]]

  /**
   * selection of 2000 is fairly arbitrary, but seems like a reasonable number
   * as its unlikley that a single flask would ever be monitoring 2k hosts,
   * or that 2k hosts would be launched in such a short order.
   */
  private val queue = boundedQueue[PlatformEvent](2048)(Chemist.defaultExecutor)

  /**
   * Initilize the chemist serivce by trying to create the various AWS resources
   * that are required to operate. Once complete, execute the init.
   *
   * There are a couple of important things to note here:
   * 1. whilst chemist will try to create an SNS topic with the specified name
   *    this still needs to be connected with your ASGs in whatever deployment
   *    configuration you happen to be using (e.g. cloudformation). This is
   *    inherently out-of-band for chemist, so we just "create" the SNS topic
   *    to ensure cheimst has the best chance of initilizing correctly. Without
   *    the aforementioend external configuration, you wont ever get any lifecycle
   *    events, and subsequently no immediete monitoring.
   *
   * 2. Given that SQS is basically a mutable queue, it does not play well when
   *    there are multiple chemist instances running. In an effort to isolate ourselves
   *    from this, the expectation is that you would be launching chemist with
   *    cloudformation and specifiying the relevant queue as an output of your template.
   *    Doing this means that every deployment you do has a its own, isolated mutable
   *    queue to work with, and when the CFN stack is deleted, the queue will be too.
   */
  lazy val init: ChemistK[Unit] = {
    log.debug("attempting to read the world of deployed instances")
    for {
      cfg <- config

      // start to wire up the topics and subscriptions to queues
      a <- SNS.create(cfg.queue.topicName)(cfg.sns).liftKleisli
      _  = log.debug(s"created sns topic with arn = $a")

      b <- cfg.discovery.lookupOne(cfg.machine.id).liftKleisli
      b1 = b.tags.get("aws:cloudformation:stack-name").getOrElse("unknown")
      _  = log.debug(s"discovered stack name for this running instance to be '$b1'")

      c <- CFN.getStackOutputs(b1)(cfg.cfn).liftKleisli
      c1 = c.get("ServiceQueueARN").getOrElse("unknown")
      _  = log.debug(s"discovered sqs queue with name '$c1'")

      _ <- SNS.subscribe(a, c1)(cfg.sns).liftKleisli
      _  = log.debug(s"subscribed sqs queue to the sns topic")

      // now the queues are setup with the right permissions,
      // start the lifecycle listener
      _ <- Pipeline.task(
             Lifecycle.stream(cfg.queue.topicName
               )(cfg.sqs, cfg.asg, cfg.ec2, cfg.discovery
               ).map(Pipeline.contextualise),
             cfg.rediscoveryInterval
           )(cfg.discovery,
             queue,
             cfg.sharder,
             cfg.http,
             cfg.state,
             sinks.unsafeNetworkIO(cfg.remoteFlask, queue)
            ).liftKleisli

      _  = log.debug("lifecycle process started")

      _ <- Task.delay(log.info(">>>>>>>>>>>> initilization complete <<<<<<<<<<<<")).liftKleisli
    } yield ()
  }
}
