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

import funnel.internals._
import Sharding.Distribution
import scalaz.concurrent.Task

/**
 * Holds a stale, non-authoritve view on the state of the world.
 * The sole purpose of this class in life is to provide some cached
 * data that the HTTP API can use to answer user questions about
 * the current state.
 */
trait StateCache {
  def event(e: PlatformEvent): Task[Unit]
  def events: Task[Seq[PlatformEvent]]

  def distribution(d: Distribution): Task[Unit]
  def distributions: Task[Distribution]

  def plan(p: Plan): Task[Unit]
  def plans: Task[Seq[Plan]]
}

object MemoryStateCache extends StateCache {
  private[funnel] val _history: BoundedStack[PlatformEvent] =
    new BoundedStack[PlatformEvent](2000)

  private[funnel] val _distribution: Ref[Distribution] =
    new Ref(Distribution.empty)

  private[funnel] val _plans: BoundedStack[Plan] =
    new BoundedStack[Plan](2000)

  def event(e: PlatformEvent): Task[Unit] =
    Task.delay(_history.push(e))

  def events: Task[Seq[PlatformEvent]] =
    Task.delay(_history.toSeq)

  def distribution(d: Distribution): Task[Unit] =
    Task.delay(_distribution.update(_ => d))

  def distributions: Task[Distribution] =
    Task.delay(_distribution.get)

  def plan(p: Plan): Task[Unit] =
    Task.delay(_plans.push(p))

  def plans: Task[Seq[Plan]] =
    Task.delay(_plans.toSeq)

}
