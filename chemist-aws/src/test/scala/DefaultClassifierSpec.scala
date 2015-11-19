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

class DefaultClassifierSpec extends FlatSpec with Matchers {
  import Classification._

  val f1 = AwsInstance("i-test1", Map(
    AwsTagKeys.name -> "flask",
    AwsTagKeys.qualifier -> "Qdf34",
    AwsTagKeys.version -> "1.1.1",
    AwsTagKeys.mirrorTemplate -> "http://@host:5775"
  ), null)

  val t1 = AwsInstance("i-test1", Map(
    AwsTagKeys.name -> "accounts",
    AwsTagKeys.qualifier -> "xxfSDdf",
    AwsTagKeys.version -> "1.2.1",
    AwsTagKeys.mirrorTemplate -> "http://@host:5775"
  ), null)

  it should "make the right assertions about flasks" in {
    DefaultClassifier.task.run(f1) should equal (ActiveFlask)
  }

  it should "make the right assertions about targets" in {
    DefaultClassifier.task.run(t1) should equal (ActiveTarget)
  }
}