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

import java.io.File
import knobs.{Config => KConfig}
import knobs.{FileResource,ClassPathResource,Required}

/**
 * A `Platform` is somewhere that hosts your application. Examples
 * of a platform would be Amazon Web Services, Mesos, your internal
 * datacenter etc.
 */
trait Platform {
  type Config <: PlatformConfig

  lazy val defaultKnobs =
    knobs.loadImmutable(Required(
      FileResource(new File("/usr/share/oncue/etc/chemist.cfg")) or
      ClassPathResource("oncue/chemist.defaults.cfg")) :: Nil)

  def config: Config
}
