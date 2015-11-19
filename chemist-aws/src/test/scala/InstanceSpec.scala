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
import scalaz.{\/,NonEmptyList}
import java.net.URI

class InstanceSpec extends FlatSpec with Matchers {
  def withTags(tags: (String,String)*): String =
    AwsInstance(
      id = "a",
      locations = NonEmptyList(Fixtures.localhost),
      tags = tags.toSeq.toMap
    ).application.get.toString

  it should "extract the right application when tags are present" in {
    withTags(
      "type" -> "foo",
      "revision" -> "1.2.3",
      "aws:cloudformation:stack-name" -> "imdev-foo-1.2.3-Fsf42fx"
    ) should equal ( "foo-1.2.3-Fsf42fx" )
  }

  it should "drop the qualifier if it is not present" in {
    withTags(
      "type" -> "foo",
      "revision" -> "1.2.3"
    ) should equal ( "foo-1.2.3" )

    // to support the old chef crap.
    withTags(
      "Name" -> "service-imdev-contentkey-2-0-5",
      "aws:cloudformation:stack-name" -> "imdev-contentkey-2-0-5"
    ) should equal ( "service-imdev-contentkey-2-0-5-unknown" )
  }

  it should "correctly generate target uris" in {
    AwsInstance(
      id = "a",
      locations = NonEmptyList(Fixtures.localhost),
      tags = Map(
        "type" -> "foo",
        "revision" -> "1.2.3",
        "aws:cloudformation:stack-name" -> "imdev-foo-1.2.3-Fsf42fx")
    ).targets should equal (Set(
      Target("foo-1.2.3-Fsf42fx",URI.create("http://127.0.0.1:5775/stream/previous")),
      Target("foo-1.2.3-Fsf42fx",URI.create("http://127.0.0.1:5775/stream/now?kind=traffic"))))
  }

}
