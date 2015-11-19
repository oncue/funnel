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

import java.util.Date

sealed abstract class AutoScalingEventKind(val notification: String)
final case object Launch extends AutoScalingEventKind("autoscaling:EC2_INSTANCE_LAUNCH")
final case object LaunchError extends AutoScalingEventKind("autoscaling:EC2_INSTANCE_LAUNCH_ERROR")
final case object Terminate extends AutoScalingEventKind("autoscaling:EC2_INSTANCE_TERMINATE")
final case object TerminateError extends AutoScalingEventKind("autoscaling:EC2_INSTANCE_TERMINATE_ERROR")
final case object TestNotification extends AutoScalingEventKind("autoscaling:TEST_NOTIFICATION")
final case object Unknown extends AutoScalingEventKind("unknown")
object AutoScalingEventKind {
  val list: List[AutoScalingEventKind] =
    List(Launch, LaunchError, Terminate, TerminateError, TestNotification)

  def find(in: String): Option[AutoScalingEventKind] =
    list.find(_.notification.toLowerCase.trim == in.toLowerCase.trim)
}

case class AutoScalingEvent(
  eventId: String,
  kind: AutoScalingEventKind,
  time: Date,
  startTime: Date,
  endTime: Date,
  instanceId: String,
  metadata: Map[String,String] = Map.empty
)
object AutoScalingEvent {
  def apply(i: String, k: AutoScalingEventKind): AutoScalingEvent =
    AutoScalingEvent(
      eventId = "manual",
      kind = k,
      time = new java.util.Date,
      startTime = new java.util.Date,
      endTime = new java.util.Date,
      instanceId = i,
      metadata = Map(
        "cause" -> "operator manually triggered lifecycle operation"
      )
    )
}