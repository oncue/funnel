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

import Sharding.Distribution
import journal.Logger
import scalaz.std.set._

trait Sharder {
  /**
   * provide the new distribution based on the result of calculating
   * how the new set should actually be distributed. main benifit here
   * is simply making the operations opaque (handling missing key cases)
   *
   * Returns values of this function represent two things:
   * 1. `Seq[(Flask,Target)]` is a sequence of targets zipped with the flask it was assigned too.
   * 2. `Distribution` is that same sequence folded into a `Distribution` instance which can
   *     then be added to the existing state of the world.
   */
  def distribution(s: Set[Target])(d: Distribution): (Seq[(Flask,Target)], Distribution)
}

object RandomSharding extends Sharder {
  private[this] val log = Logger[RandomSharding.type]
  private[this] val rnd = new scala.util.Random

  // Randomly assigns each target in `s` to a Flask in the distribution `d`.
  private def calculate(s: Set[Target])(d: Distribution): Seq[(Flask,Target)] = {
    val flasks = Sharding.shards(d)
    val range = flasks.indices
    if(flasks.size == 0) Nil
    else {
      s.toList.map { t =>
        flasks(rnd.nextInt(range.length)) -> t
      }
    }
  }

  /**
   * Assign the targets in `s` randomly to the distribution in `d`.
   * Returns a pair of:
   *   The assignment of targets to flasks
   *   the new distribution
   *
   * Throws away the existing assignments UNLESS `s` is empty,
   * in which case it leaves `d` unchanged.
   *
   * `s`: The targets to distribute
   * `d`: The existing distribution
   */
  def distribution(s: Set[Target])(d: Distribution): (Seq[(Flask,Target)], Distribution) = {
    if(s.isEmpty) (Seq.empty,d)
    else {
      log.debug(s"distribution: attempting to distribute targets '${s.mkString(",")}'")
      val work = calculate(s)(d)

      log.debug(s"distribution: work = $work")

      val dist = work.foldLeft(d) { (a,b) => a.updateAppend(b._1, Set(b._2)) }

      log.debug(s"work = $work, dist = $dist")

      (work, dist)
    }
  }
}

/**
 * Implements a "least-first" round-robin sharding. The flasks are ordered
 * by the amount of work they currently have assigned, with the least
 * amount of work is ordered first, and then we round-robin the nodes in the
 * hope that most sharding calls happen when instances come online in small
 * groups.
 *
 * Downside of this sharder is that it often leads to "heaping" where over all
 * flask shards, the work is "heaped" to one end of the distribution curve.
 */
object LFRRSharding extends Sharder {
  private[this] val log = Logger[LFRRSharding.type]

  private def calculate(s: Set[Target])(d: Distribution): Seq[(Flask,Target)] = {
    val servers: IndexedSeq[Flask] = Sharding.shards(d)
    val ss                    = servers.size
    val input: Set[Target]    = Sharding.deduplicate(s)(d)

    log.debug(s"calculating the target distribution: servers=$servers, input=$input")

    if(ss == 0) {
      log.warn("there are no flask servers currently registered to distribute work too.")
      Nil // needed for when there are no Flask's in-memory; causes SOE.
    } else {
      // interleave the input with the known flask servers ordered by the
      // flask that currently has the least amount of work assigned.
      input.toStream.zip(Stream.continually(servers).flatten).toList.map(t => (t._2, t._1))
    }
  }

  def distribution(s: Set[Target])(d: Distribution): (Seq[(Flask,Target)], Distribution) = {
    // this check is needed as otherwise the fold gets stuck in a gnarly
    // infinate loop, and this function never completes.
    if(s.isEmpty) (Seq.empty,d)
    else {
      log.debug(s"distribution: attempting to distribute targets '${s.mkString(",")}'")
      val work = calculate(s)(d)

      log.debug(s"distribution: work = $work")

      val dist = work.foldLeft(d) { (a,b) => a.updateAppend(b._1, Set(b._2)) }

      log.debug(s"work = $work, dist = $dist")

      (work, dist)
    }
  }
}
