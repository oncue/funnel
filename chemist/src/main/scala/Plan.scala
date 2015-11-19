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
import scalaz.concurrent.Task
/**
 * Represents the set of effects that will be applied to a given input
 */
sealed trait Plan

/**
 * Actually distribute the work to the flasks. the supplied `work`
 * param should contain only the work that needs to sent to a flask
 * to actually be monitored.
 */
case class Distribute(work: Distribution) extends Plan

/**
 * Redistribute work in the cluster of flasks by stopping some work
 * and starting again on a different node. Work to be stopped/started
 * should be specified completely, and any work that is not to be
 * moved to another shard should be completely omitted.
 */
case class Redistribute(stop: Distribution, start: Distribution) extends Plan {
  import scalaz.std.set._

  /**
   * convenience for updating a specific flask shard with work to stop
   * and work to start. Typically this function assumes that the input
   * distributions were empty, as update append will use any existing
   * data and just shove the new stuff on the end, which might not be
   * what you want if the distributions are non-empty.
   */
  def update(shard: Flask, stopping: Set[Target], starting: Set[Target]): Redistribute =
    this.copy(
      stop  = stop.updateAppend(shard, stopping),
      start = start.updateAppend(shard, starting)
    )
}
object Redistribute {
  val empty = Redistribute(Distribution.empty, Distribution.empty)
}

/**
 * given a task that produces a sequence of `PlatformEvent`, we can
 * use this to propagate synthetic production of platform events down
 * to the effectful sinks.
 *
 * the classic use case for this functionality is when we loose a flask
 * and the flask is therefore non-discoverable, we convert the
 * `TerminatedFlask` event into a `Produce` with a `Task` that does
 * rediscovery, and then pump that back into the head of the platform
 * event stream, triggering the lost work to be resharded.
 */
case class Produce(f: Task[Seq[PlatformEvent]]) extends Plan

/**
 * No effects to execute here, just bottom out with Unit
 */
case object Ignore extends Plan