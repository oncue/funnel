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

import scalaz.concurrent.Task
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.services.cloudformation.{AmazonCloudFormation,AmazonCloudFormationClient}
import com.amazonaws.services.cloudformation.model.DescribeStacksRequest

object CFN {
  import scala.collection.JavaConverters._

  def client(
    credentials: BasicAWSCredentials,
    awsProxyHost: Option[String] = None,
    awsProxyPort: Option[Int] = None,
    awsProxyProtocol: Option[String] = None,
    region: Region = Region.getRegion(Regions.fromName("us-east-1"))
  ): AmazonCloudFormation = { //cfg.require[String]("aws.region"))
    val client = new AmazonCloudFormationClient(
      credentials,
      proxy.configuration(awsProxyHost, awsProxyPort, awsProxyProtocol))
    client.setRegion(region)
    client
  }

  /**
   * Get a list of all the declared "outputs" for a given clouformation stack.
   * As far as chemist is concerened, this is needed because we want to figure
   * out where our SQS queue was created.
   */
  def getStackOutputs(name: String)(cfn: AmazonCloudFormation): Task[Map[String,String]] = {
    val req = (new DescribeStacksRequest).withStackName(name)
    for {
      a <- Task(cfn.describeStacks(req))(funnel.chemist.Chemist.defaultPool)
      b  = a.getStacks.asScala.flatMap(_.getOutputs.asScala)
    } yield b.map(x => x.getOutputKey -> x.getOutputValue).toMap
  }
}
