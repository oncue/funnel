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

/**
 * Given locations are modeling where a monitorable thing resides,
 * and it `Location` is somewhat overloaded as its used for both
 * admin channels and mirroring channels, `LocationIntent` serves
 * as a discriminator for sequences of Locations.
 */
sealed trait LocationIntent

object LocationIntent {

  /**
   * Make a best effort to convert a string into a `LocationIntent`.
   * All strings are lowercased and trimmed:
   * `mirroring`, `supervision`, `ignored`
   */
  def fromString(s: String): Option[LocationIntent] =
    all.find(_.toString.toLowerCase == s.toLowerCase)

  lazy val all = List(Mirroring, Ignored)

  /**
   * locations destined for mirroring, should be
   * accessible for Flask to come and collect metrics on.
   * The protocol should be determined by the templates.
   */
  case object Mirroring extends LocationIntent

  /**
   * locations that are meant to be ignored are few and far
   * between, but this is supplied for completeness. One
   * case where `Ignored` is useful is when running multiple
   * clusters of flasks during a migration from one cluster
   * to another. The "old" flask cluster should be ignored by
   * the new flasks, and thus have their locations ignored
   * by chemist. If the old flasks are mirrored by the new
   * flasks (and vice versa), there would essentially be a
   * cascading amplication DOS attack of the system, by itself.
   */
  case object Ignored extends LocationIntent
}
