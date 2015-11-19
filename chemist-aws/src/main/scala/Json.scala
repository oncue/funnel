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

object JSON {
  import argonaut._, Argonaut._
  import funnel.chemist.JSON._

  implicit val JsonToAutoScalingEventKind: DecodeJson[AutoScalingEventKind] =
    DecodeJson(c => for {
                 a <- (c --\ "Event").as[String]
               } yield AutoScalingEventKind.find(a).getOrElse(Unknown))

  implicit def JsonToJavaDate(name: String): DecodeJson[java.util.Date] =
    DecodeJson(c => (c --\ name).as[String].map(_.asDate))

  /**
   * {
   *   "StatusCode": "InProgress",
   *   "Service": "AWS Auto Scaling",
   *   "AutoScalingGroupName": "imdev-su-4-1-264-cSRykpc-WebServiceAutoscalingGroup-1X7QT7QEZKKC7",
   *   "Description": "Terminating EC2 instance: i-dd947af7",
   *   "ActivityId": "926c4ae3-8181-4668-bcd1-6febc7668d18",
   *   "Event": "autoscaling:EC2_INSTANCE_TERMINATE",
   *   "Details": {
   *     "Availability Zone": "us-east-1b"
   *   },
   *   "AutoScalingGroupARN": "arn:aws:autoscaling:us-east-1:465404450664:autoScalingGroup:cf59efeb-6e6e-40c3-90a8-804662f400c7:autoScalingGroupName/imdev-su-4-1-264-cSRykpc-WebServiceAutoscalingGroup-1X7QT7QEZKKC7",
   *   "Progress": 50,
   *   "Time": "2014-07-31T18:30:41.244Z",
   *   "AccountId": "465404450664",
   *   "RequestId": "926c4ae3-8181-4668-bcd1-6febc7668d18",
   *   "StatusMessage": "",
   *   "EndTime": "2014-07-31T18:30:41.244Z",
   *   "EC2InstanceId": "i-dd947af7",
   *   "StartTime": "2014-07-31T18:30:35.406Z",
   *   "Cause": "At 2014-07-31T18:30:35Z an instance was taken out of service in response to a EC2 health check indicating it has been terminated or stopped."
   * }
   */
  implicit val JsonToAutoScalingEvent: DecodeJson[AutoScalingEvent] =
    DecodeJson(input => for {
      a <- (input --\ "ActivityId").as[String]
      b <- JsonToAutoScalingEventKind(input)
      c <- (input --\ "AutoScalingGroupName").as[String]
      d <- (input --\ "AutoScalingGroupARN").as[String]
      e <- (input --\ "Details" --\ "Availability Zone").as[String]
      f <- (input --\ "Description").as[String]
      g <- (input --\ "Cause").as[String]
      h <- (input --\ "Progress").as[Int]
      i <- (input --\ "AccountId").as[String]
      j <- JsonToJavaDate("Time")(input)
      k <- JsonToJavaDate("StartTime")(input)
      l <- JsonToJavaDate("EndTime")(input)
      m <- (input --\ "EC2InstanceId").as[String]
    } yield AutoScalingEvent(
      eventId    = a,
      kind       = b,
      time       = j,
      startTime  = k,
      endTime    = l,
      instanceId = m,
      metadata = Map(
        "asg-arn"  -> d,
        "asg-name" -> c,
        "description" -> f,
        "cause" -> g,
        "account-id" -> i,
        "datacenter" -> e
      )
    ))

  /**
   * {
   *   "activity-id": "foo",
   *   "instance-id": "i-353534",
   *   "start-time": "2014....",
   *   "end-time": "2014....",
   *   "date-time": "2014....",
   *   "kind": "launch",
   * }
   */
  implicit val AutoScalingEventToJson: EncodeJson[AutoScalingEvent] =
    EncodeJson((e: AutoScalingEvent) =>
      ("activity-id" := e.eventId) ->:
      ("instance-id" := e.instanceId) ->:
      ("start-time"  := e.startTime.toString) ->:
      ("end-time"    := e.endTime.toString) ->:
      ("date-time"   := e.time.toString) ->:
      ("kind"        := e.kind.notification) ->: jEmptyObject
    )
}
