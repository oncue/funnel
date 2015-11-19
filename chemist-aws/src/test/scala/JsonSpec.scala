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

import org.scalatest.{FlatSpec,Matchers}
import scalaz.\/

class JsonSpec extends FlatSpec with Matchers {
  import chemist.JSON._
  import aws.JSON._
  import argonaut._, Argonaut._

  it should "correctly parse the incoming JSON from AWS ASG notifications" in {
    Parse.decodeOption[AutoScalingEvent](Fixtures.asgEvent(Terminate, name = "foo", instanceId = "i-dd947af7")
      ).get should equal (
        AutoScalingEvent(
          eventId      = "926c4ae3-8181-4668-bcd1-6febc7668d18",
          kind        = Terminate,
          time        = "2014-07-31T18:30:41.244Z".asDate,
          startTime   = "2014-07-31T18:30:35.406Z".asDate,
          endTime     = "2014-07-31T18:30:41.244Z".asDate,
          instanceId  = "i-dd947af7",
          metadata    = Map(
            "asg-arn" -> "...",
            "description" -> "test",
            "account-id" -> "465404450664",
            "cause" -> "At 2014-07-31T18:30:35Z ...",
            "datacenter" -> "us-east-1b",
            "asg-name" -> "foo"
          )
        )
      )
  }
}
