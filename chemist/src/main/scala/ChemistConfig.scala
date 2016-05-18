//: ----------------------------------------------------------------------------
//: Copyright (C) 2016 Verizon.  All Rights Reserved.
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

import knobs._

/**
 * Used to figure out where this particular chemist
 * is running, which is going to be useful for figuring
 * out service-locality in the future.
 */
case class MachineConfig(
  id: String,
  location: Location
)

object ChemistConfig {

  /** Currently supports only `"memory"` */
  def readStateCache(c: Option[String]): StateCache =
    c match {
      case Some("memory") => MemoryStateCache
      case _              => MemoryStateCache
    }

  /** Supports `"least-first-round-robin"` or `"random"`, defaulting to random. */
  def readSharder(c: Option[String]): Sharder =
    c match {
      case Some("least-first-round-robin") => LFRRSharding
      case Some("random")                  => RandomSharding
      case _                               => RandomSharding
    }


  /** Requires `host` (String) and `port` (Int) fields on `cfg` */
  def readNetwork(cfg: Config): NetworkConfig =
    NetworkConfig(cfg.require[String]("host"), cfg.require[Int]("port"))

  
}
