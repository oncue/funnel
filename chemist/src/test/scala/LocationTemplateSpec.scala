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

import org.scalatest._

class LocationTemplateSpec extends FlatSpec with Matchers {
  it should "correctly replace @host" in {
    LocationTemplate("http://@host:5555").build("@host" -> "foo.com"
      ) should equal ("http://foo.com:5555")
  }

  it should "correctly replace @host and @port" in {
    LocationTemplate("http://@host:@port").build("@host" -> "foo.com", "@port" -> "4444"
      ) should equal ("http://foo.com:4444")
  }

  it should "correctly replace @host and ignore @port when not specified" in {
    LocationTemplate("http://@host:@port").build("@host" -> "foo.com"
      ) should equal ("http://foo.com:@port")
  }

  it should "ignore all variables when no replacements are specified" in {
    LocationTemplate("http://@host:@port").build(
      ) should equal ("http://@host:@port")
  }
}

