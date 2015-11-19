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
package http

import org.scalacheck._, Prop._, Arbitrary._
import java.net.URI
import Monitoring.formatURI

object HttpMonitoringSpec extends Properties("monitoring.http"){
  import argonaut.{DecodeJson, EncodeJson, Parse}

  def roundTrip[A:EncodeJson:DecodeJson](a: A): Prop = {
    val out1: String = implicitly[EncodeJson[A]].apply(a).nospaces
    // println(out1)
    val parsed = Parse.decode[A](out1).toOption.get
    val out2: String = implicitly[EncodeJson[A]].apply(parsed).nospaces
    out1 == out2 || (s"out1: $out1, out2: $out2" |: false)
  }

  property("NaN handling") = secure {
    import JSON._
    roundTrip(Stats.statsGroup.zero) &&
    roundTrip(Double.NaN) &&
    roundTrip(Double.PositiveInfinity) &&
    roundTrip(Double.NegativeInfinity)
  }

  property("prettyURL") = secure {
    import Monitoring._
    val i1 = new URI("http://google.com:80/foo/bar/baz")
    val i2 = new URI("http://google.com/foo/bar/baz")
    val i3 = new URI("http://google.com:80")
    val i4 = new URI("http://google.com")
    val o = (formatURI(i1), formatURI(i2), formatURI(i3), formatURI(i4))
    o._1 == "google.com-80/foo/bar/baz" &&
    o._2 == "google.com/foo/bar/baz" &&
    o._3 == "google.com-80" &&
    o._4 == "google.com" || ("" + o |: false)
  }
}

