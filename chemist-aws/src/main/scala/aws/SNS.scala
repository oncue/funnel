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
package aws

import com.amazonaws.auth.{AWSCredentialsProvider, AWSCredentials}
import com.amazonaws.services.sns.{AmazonSNS,AmazonSNSClient}
import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.services.sns.model.{CreateTopicRequest, PublishRequest}
import com.amazonaws.auth.BasicAWSCredentials
import scalaz.concurrent.Task
import funnel.chemist.Chemist
object SNS {

  def client(
    credentials: BasicAWSCredentials,
    awsProxyHost: Option[String] = None,
    awsProxyPort: Option[Int] = None,
    awsProxyProtocol: Option[String] = None,
    region: Region = Region.getRegion(Regions.fromName("us-east-1"))
  ): AmazonSNS = { //cfg.require[String]("aws.region"))
    val client = new AmazonSNSClient(
      credentials,
      proxy.configuration(awsProxyHost, awsProxyPort, awsProxyProtocol))
    client.setRegion(region)
    client
  }

  def create(topicName: String)(client: AmazonSNS): Task[ARN] = Task {
    val req = new CreateTopicRequest(topicName) // cfg.require[String]("aws.snsTopic")
    client.createTopic(req).getTopicArn // idempotent operation
  }(Chemist.serverPool)

  def publish(topicName: String, payload: String)(client: AmazonSNS): Task[Unit] = {
    create(topicName)(client).flatMap { arn =>
      Task {
        val preq = new PublishRequest(arn, payload)
        val pres = client.publish(preq)
      }(Chemist.serverPool)
    }
  }

  def subscribe(snsArn: ARN, targetArn: ARN, protocol: String = "sqs")(client: AmazonSNS): Task[ARN] =
    for {
      a <- Task(client.subscribe(snsArn, protocol, targetArn).getSubscriptionArn)(Chemist.serverPool)
      b <- Task(client.setSubscriptionAttributes(a, "RawMessageDelivery", "true"))(Chemist.serverPool)
    } yield a
}
