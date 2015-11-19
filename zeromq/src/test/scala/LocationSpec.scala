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
package zeromq

import org.scalatest.{FlatSpec,Matchers}
import java.net.URI
import scalaz.\/

class LocationSpec extends FlatSpec with Matchers {

  def lift(s: String): Option[Location] =
    Location(new URI(s)).toOption

  "location companion" should "parse from a URI" in {
    lift("ipc:///foo/bar.socket").map(_.hostOrPath
      ) should equal(Some("/foo/bar.socket"))

    lift("tcp://foo.com:7777").map(_.hostOrPath
      ) should equal(Some("foo.com:7777"))

    lift("foo:///tmp/bar.socket").map(_.hostOrPath
      ) should equal(None)

    lift("zeromq+tcp://10.0.0.0:8888/dooo").map(_.hostOrPath
      ) should equal (Some("10.0.0.0:8888"))

    lift("zeromq+ipc:///var/run/bar.sock").map(_.hostOrPath
      ) should equal (Some("/var/run/bar.sock"))
  }
}