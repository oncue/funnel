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

import com.amazonaws.services.ec2.model.{Instance => EC2Instance}
import com.amazonaws.services.ec2.model.{Placement,Tag}
import zeromq.TCP

object Fixtures {

  def instance(
    id: String,
    datacenter: String = "us-east-1b",
    privateDns: String = "foo.internal",
    publicDns: String = "foo.ext.amazonaws.com",
    tags: Seq[(String,String)] = Seq("baz" -> "v1","bar" -> "v2")
  ): EC2Instance =
    (new EC2Instance).withInstanceId(id)
    .withPlacement(new Placement(datacenter))
    .withPrivateDnsName(privateDns)
    .withPublicDnsName(publicDns)
    .withTags(tags.map { case (k,v) => new Tag(k,v) }:_*)

  val defaultTemplates =
    LocationTemplate("http://@host:@port/stream/previous") ::
    LocationTemplate("http://@host:@port/stream/now?kind=traffic") :: Nil

  val instances: Seq[EC2Instance] =
    instance("i-dx947af7") ::
    instance("i-15807647") ::
    instance("i-flaskAAA",
      tags = Vector(AwsTagKeys.name -> "flask")
    ) :: Nil

  val localhost: Location =
    Location(
      host = "127.0.0.1",
      port = 5775,
      datacenter = "local",
      protocol = NetworkScheme.Http,
      intent = LocationIntent.Mirroring,
      templates = defaultTemplates)

  def asgEvent(
    kind: AutoScalingEventKind,
    name: String = "test-group",
    instanceId: String = "instance-id-goes-here"
  ): String = s"""
    |{
    |  "StatusCode": "InProgress",
    |  "Service": "AWS Auto Scaling",
    |  "AutoScalingGroupName": "${name}",
    |  "Description": "test",
    |  "ActivityId": "926c4ae3-8181-4668-bcd1-6febc7668d18",
    |  "Event": "${kind.notification}",
    |  "Details": {
    |    "Availability Zone": "us-east-1b"
    |  },
    |  "AutoScalingGroupARN": "...",
    |  "Progress": 50,
    |  "Time": "2014-07-31T18:30:41.244Z",
    |  "AccountId": "465404450664",
    |  "RequestId": "926c4ae3-8181-4668-bcd1-6febc7668d18",
    |  "StatusMessage": "",
    |  "EndTime": "2014-07-31T18:30:41.244Z",
    |  "EC2InstanceId": "${instanceId}",
    |  "StartTime": "2014-07-31T18:30:35.406Z",
    |  "Cause": "At 2014-07-31T18:30:35Z ..."
    |}
    """.stripMargin
}
