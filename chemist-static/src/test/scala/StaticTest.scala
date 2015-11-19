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
package static

import org.scalatest._
import java.io.File
import knobs.{FileResource,ClassPathResource,Required}
import scalaz.concurrent.Task

class StaticTest extends FlatSpec with Matchers {
  it should "load Instances from chemist.cfg" in {
    val instances = (for {
      base   <- (knobs.load(Required(
        ClassPathResource("oncue/chemist.cfg")) :: Nil))
      cfg    <- Config.readConfig(base)
    } yield cfg.targets).run

    val x: Boolean = instances.exists {
      case (TargetID("instance1"), targets) =>
        targets.size == 1 && targets.foldLeft(true)((b,t) => t.uri.getPort()==1234)
      case _          => false
    } &&
    instances.exists {
      case (TargetID("instance2"), targets) =>
        targets.size == 1 && targets.foldLeft(true)((b,t) => t.uri.getPort() == 5678)
      case _          => false
    }
    x should be (true)
  }
}
