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

import scalaz.{==>>,Order}
import scalaz.concurrent.Task
import funnel.ClusterName
import java.net.URI
import journal.Logger

object Sharding {

  type Distribution = Flask ==>> Set[Target]

  object Distribution {
    def empty: Distribution = ==>>()
  }

  private lazy val log = Logger[Sharding.type]

  /**
   * obtain a list of flasks ordered by flasks with the least
   * assigned work first.
   */
  def shards(d: Distribution): IndexedSeq[Flask] =
    sorted(d).map(_._1).toIndexedSeq

  /**
   * sort the current distribution by the size of the url
   * set currently assigned to the index flask. Resulting
   * snapshot is ordered by flasks with least assigned
   * work first.
   */
  def sorted(d: Distribution): Seq[(Flask, Set[Target])] =
    d.toList.sortBy(_._2.size)

  /**
   * dump out the current snapshot of how chemist believes work
   * has been assigned to flasks.
   */
  def snapshot(d: Distribution): Map[FlaskID, Map[ClusterName, List[URI]]] =
    d.toList.map { case (i,s) =>
      i.id -> s.groupBy(_.cluster).mapValues(_.toList.map(_.uri))
    }.toMap

  /**
   * obtain the entire set of what chemist views as the
   * distributed world of urls.
   */
  def targets(d: Distribution): Set[Target] =
    d.values match {
      case Nil   => Set.empty[Target]
      case other => other.reduceLeft(_ ++ _)
    }

  /**
   * Given a set of inputs, check against the current known set of urls
   * that we're not already monitoring the inputs (thus ensuring that
   * the cluster is not having duplicated monitoring items)
   */
  private[chemist] def deduplicate(next: Set[Target])(d: Distribution): Set[Target] = {
    // get the full list of targets we currently know about
    val existing = targets(d)

    // determine if any of the supplied urls are existing targets
    val delta = next.map(_.uri) &~ existing.map(_.uri)

    // having computed the targets that we actually care about,
    // rehydrae a `Set[Target]` from the given `Set[SafeURL]`
    delta.foldLeft(Set.empty[Target]){ (a,b) =>
      a ++ next.filter(_.uri == b)
    }
  }
}