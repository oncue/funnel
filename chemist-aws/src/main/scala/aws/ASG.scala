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

import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.auth.{AWSCredentials,BasicAWSCredentials}
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.Address
import com.amazonaws.services.autoscaling.{AmazonAutoScaling,AmazonAutoScalingClient}
import com.amazonaws.services.autoscaling.model.{
  DescribeAutoScalingGroupsRequest,
  AutoScalingGroup,Instance => ASGInstance}
import scalaz.concurrent.Task
import scala.collection.JavaConverters._

case class Group(
  name: String,
  instances: Seq[ASGInstance] = Nil,
  tags: Map[String,String] = Map.empty)

case class ASGNotFoundException(asgName: String) extends RuntimeException {
  override def getMessage: String = s"Unable to find the specified ASG in AWS: '$asgName'"
}

object ASG {

  def client(
    credentials: BasicAWSCredentials,
    awsProxyHost: Option[String] = None,
    awsProxyPort: Option[Int] = None,
    awsProxyProtocol: Option[String] = None,
    region: Region = Region.getRegion(Regions.fromName("us-east-1"))
  ): AmazonAutoScaling = { //cfg.require[String]("aws.region"))
    val client = new AmazonAutoScalingClient(
      credentials,
      proxy.configuration(awsProxyHost, awsProxyPort, awsProxyProtocol))
    client.setRegion(region)
    client
  }

  import annotation.tailrec

  def list(asg: AmazonAutoScaling): Task[Seq[Group]] = {
    @tailrec def fetch(result: => Seq[AutoScalingGroup], token: Option[String] = None): Seq[AutoScalingGroup] = {
      val req = new DescribeAutoScalingGroupsRequest().withMaxRecords(100)
      val r = token.map(t => asg.describeAutoScalingGroups(req.withNextToken(t))
          ).getOrElse(asg.describeAutoScalingGroups(req))
      val l = r.getAutoScalingGroups.asScala.toList

      val aggregated = l ++ result

      if(r.getNextToken != null) fetch(aggregated, Option(r.getNextToken))
      else aggregated
    }

    Task(fetch(Nil).map(g =>
      Group(
        name      = g.getAutoScalingGroupName,
        instances = g.getInstances.asScala.toSeq,
        tags      = tags(g))
      ))(funnel.chemist.Chemist.serverPool)
  }

  def lookupByName(name: String)(asg: AmazonAutoScaling): Task[AutoScalingGroup] = {
    val req = (new DescribeAutoScalingGroupsRequest
      ).withMaxRecords(1
      ).withAutoScalingGroupNames(name)

    Task(asg.describeAutoScalingGroups(req))(funnel.chemist.Chemist.serverPool).flatMap { r =>
      val opt: Option[AutoScalingGroup] = r.getAutoScalingGroups.asScala.toList.headOption
      val fail: Task[AutoScalingGroup] = Task.fail(ASGNotFoundException(name))
      opt.fold(fail)(Task.delay(_))
    }
  }

  private def tags(g: AutoScalingGroup): Map[String,String] =
    g.getTags.asScala.map(t => t.getKey -> t.getValue).toMap

}
