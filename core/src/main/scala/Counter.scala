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

import spire.math.Natural
import scalaz.concurrent.Task

/**
 * An instrument to count events, keep a tally, or other monotonically increasing ordinal number.
 * For example: the number of dropped connections, the number of cars that have passed by.
 *
 * NOT appropriate for tracking sums or amounts, such as the number of currently connected
 * users, the amount of money in a bank account, etc.
 */
class Counter(underlying: PeriodicGauge[Double]) extends Instrument[Periodic[Double]] { self =>
  def keys = underlying.keys

  /** Add the given number to the current count. */
  def add(n: Natural): Task[Unit] =
    underlying add n.doubleValue

  /** Advance the counter by one. */
  def advance: Task[Unit] = add(Natural.one)

  // --- Unsafe API ---

  /** UNSAFE. Add the given number to the current tally. */
  def incrementBy(by: Int): Unit = add(Natural(by)).run
  /** UNSAFE. Add one to the current tally. */
  def increment: Unit = advance.run
}
