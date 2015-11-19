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

import org.scalacheck._
import Prop._
import Arbitrary._

object TransportedSpec extends Properties("Transported") {
  implicit val arbitraryScheme = Arbitrary(Gen.oneOf(Schemes.fsm,Schemes.telemetry/*,Schemes.unknown*/))
  implicit val arbitraryVersion = Arbitrary(Gen.oneOf(Versions.v1, Versions.v2/*, Versions.unknown*/))
  implicit val arbitraryWindow = Arbitrary(Gen.oneOf(Windows.now, Windows.sliding, Windows.previous/*, Windows.unknown*/))
  implicit val arbitraryTopic = Arbitrary(arbitrary[String].map(Topic(_)))
  implicit val arbitraryTransported = Arbitrary(for {
    ser <- arbitrary[Serial]
    sch <- arbitrary[Scheme]
    ver <- arbitraryVersion.arbitrary
    win <- arbitrary[Option[Window]]
    top <- arbitrary[Option[Topic]]
    byt <- arbitrary[Array[Byte]]
  } yield Transported(ser, sch, ver, win, top, byt))

  property("roundtrip")  = forAll { (t: Transported) =>
    val rt = Transported(t.header, t.serial, t.bytes)
    t == rt
  }
}