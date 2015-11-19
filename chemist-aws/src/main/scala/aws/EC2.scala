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
import com.amazonaws.services.ec2.{AmazonEC2,AmazonEC2Client}
import com.amazonaws.services.ec2.model.{
  DescribeAddressesRequest,
  DescribeAddressesResult,
  Filter,
  Address,
  DescribeInstancesRequest,
  DescribeInstancesResult,
  Reservation}
import scalaz.concurrent.Task
import scala.collection.JavaConverters._

object EC2 {

  def client(
    credentials: BasicAWSCredentials,
    awsProxyHost: Option[String] = None,
    awsProxyPort: Option[Int] = None,
    awsProxyProtocol: Option[String] = None,
    region: Region = Region.getRegion(Regions.fromName("us-east-1"))
  ): AmazonEC2 = { //cfg.require[String]("aws.region"))
    val client = new AmazonEC2Client(
      credentials,
      proxy.configuration(awsProxyHost, awsProxyPort, awsProxyProtocol))
    client.setRegion(region)
    client
  }

  import annotation.tailrec

  def reservations(ids: Seq[String])(ec2: AmazonEC2): Task[Seq[Reservation]] = {
    @tailrec def fetch(result: Seq[Reservation], token: Option[String] = None): Seq[Reservation] = {
      val req = new DescribeInstancesRequest().withInstanceIds(ids:_*)
      val r = token.map(t => ec2.describeInstances(req.withNextToken(t))
          ).getOrElse(ec2.describeInstances(req))
      val l = r.getReservations.asScala.toSeq

      val aggregated = l ++ result

      if(r.getNextToken != null) fetch(aggregated, Option(r.getNextToken))
      else aggregated
    }

    Task(fetch(Nil))(funnel.chemist.Chemist.serverPool)
  }
}
