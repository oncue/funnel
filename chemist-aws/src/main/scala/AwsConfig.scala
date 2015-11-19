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

import knobs._
import funnel.aws._
import dispatch.Http
import java.net.InetAddress
import scalaz.concurrent.Strategy
import scalaz.stream.async.signalOf
import concurrent.duration.Duration
import com.amazonaws.services.sns.AmazonSNS
import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.regions.{Regions,Region}
import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.cloudformation.AmazonCloudFormation

/**
 * Settings needed to subscribe ot the appropriate queues
 * used by Chemist in AWS.
 */
case class QueueConfig(
  topicName: String
)

/**
 * Used to figure out where this particular chemist
 * is running, which is going to be useful for figuring
 * out service-locality in the future.
 */
case class MachineConfig(
  id: String,
  location: Location
)

case class AwsConfig(
  templates: List[LocationTemplate],
  network: NetworkConfig,
  machine: MachineConfig,
  queue: QueueConfig,
  sns: AmazonSNS,
  sqs: AmazonSQS,
  ec2: AmazonEC2,
  asg: AmazonAutoScaling,
  cfn: AmazonCloudFormation,
  commandTimeout: Duration,
  sharder: Sharder,
  classifier: Classifier[AwsInstance],
  state: StateCache,
  rediscoveryInterval: Duration
) extends PlatformConfig {

  val discovery: AwsDiscovery =
    new AwsDiscovery(ec2, asg, classifier, templates)

  val http: Http = Http.configure(
    _.setAllowPoolingConnection(true)
     .setConnectionTimeoutInMs(commandTimeout.toMillis.toInt))

  val remoteFlask = new HttpFlask(http)
}

object AwsConfig {
  def readConfig(cfg: Config): AwsConfig = {
    val topic     = cfg.require[String]("chemist.sns-topic-name")
    val templates = cfg.require[List[String]]("chemist.target-resource-templates")
    val aws       = cfg.subconfig("aws")
    val network   = cfg.subconfig("chemist.network")
    val timeout   = cfg.require[Duration]("chemist.command-timeout")
    val sharding  = cfg.lookup[String]("chemist.sharding-strategy")
    val cachetype = cfg.lookup[String]("chemist.state-cache")
    val classifiy = cfg.lookup[String]("chemist.classification-stratagy")
    val interval  = cfg.require[Duration]("chemist.rediscovery-interval")
    AwsConfig(
      templates 		      = templates.map(LocationTemplate),
      network             = readNetwork(network),
      queue               = QueueConfig(topic),
      sns                 = readSNS(aws),
      sqs                 = readSQS(aws),
      ec2                 = readEC2(aws),
      asg                 = readASG(aws),
      cfn                 = readCFN(aws),
      sharder             = readSharder(sharding),
      classifier          = readClassifier(classifiy),
      commandTimeout      = timeout,
      machine             = readMachineConfig(cfg),
      rediscoveryInterval = interval,
      state               = readStateCache(cachetype)
    )
  }

  private def readClassifier(c: Option[String]): Classifier[AwsInstance] =
    c match {
      case Some("default") => DefaultClassifier
      case _               => DefaultClassifier
    }

  private def readMachineConfig(cfg: Config): MachineConfig =
    MachineConfig(
      id = cfg.lookup[String]("aws.instance-id").getOrElse("i-localhost"),
      location = Location(
        host = cfg.lookup[String]("aws.network.local-ipv4").getOrElse(InetAddress.getLocalHost.getHostName),
        port = cfg.require[Int]("chemist.network.funnel-port"),
        datacenter = cfg.require[String]("aws.region"),
        intent = LocationIntent.Mirroring,
        templates = Nil
      )
    )

  private def readStateCache(c: Option[String]): StateCache =
    c match {
      case Some("memory") => MemoryStateCache
      case _              => MemoryStateCache
    }

  private def readSharder(c: Option[String]): Sharder =
    c match {
      case Some("least-first-round-robin") => LFRRSharding
      case Some("random")                  => RandomSharding
      case _                               => RandomSharding
    }

  private def readNetwork(cfg: Config): NetworkConfig =
    NetworkConfig(cfg.require[String]("host"), cfg.require[Int]("port"))

  private def readCFN(cfg: Config): AmazonCloudFormation =
    CFN.client(
      new BasicAWSCredentials(
        cfg.require[String]("access-key"),
        cfg.require[String]("secret-key")),
      cfg.lookup[String]("proxy-host"),
      cfg.lookup[Int]("proxy-port"),
      cfg.lookup[String]("proxy-protocol"),
      Region.getRegion(Regions.fromName(cfg.require[String]("region")))
    )

  private def readSNS(cfg: Config): AmazonSNS =
    SNS.client(
      new BasicAWSCredentials(
        cfg.require[String]("access-key"),
        cfg.require[String]("secret-key")),
      cfg.lookup[String]("proxy-host"),
      cfg.lookup[Int]("proxy-port"),
      cfg.lookup[String]("proxy-protocol"),
      Region.getRegion(Regions.fromName(cfg.require[String]("region")))
    )

  private def readSQS(cfg: Config): AmazonSQS =
    SQS.client(
      new BasicAWSCredentials(
        cfg.require[String]("access-key"),
        cfg.require[String]("secret-key")),
      cfg.lookup[String]("proxy-host"),
      cfg.lookup[Int]("proxy-port"),
      cfg.lookup[String]("proxy-protocol"),
      Region.getRegion(Regions.fromName(cfg.require[String]("region")))
    )

  private def readASG(cfg: Config): AmazonAutoScaling =
    ASG.client(
      new BasicAWSCredentials(
        cfg.require[String]("access-key"),
        cfg.require[String]("secret-key")),
      cfg.lookup[String]("proxy-host"),
      cfg.lookup[Int]("proxy-port"),
      cfg.lookup[String]("proxy-protocol"),
      Region.getRegion(Regions.fromName(cfg.require[String]("region")))
    )

  private def readEC2(cfg: Config): AmazonEC2 =
    EC2.client(
      new BasicAWSCredentials(
        cfg.require[String]("access-key"),
        cfg.require[String]("secret-key")),
      cfg.lookup[String]("proxy-host"),
      cfg.lookup[Int]("proxy-port"),
      cfg.lookup[String]("proxy-protocol"),
      Region.getRegion(Regions.fromName(cfg.require[String]("region")))
    )

}
