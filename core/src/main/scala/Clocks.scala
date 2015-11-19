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

import java.util.concurrent.{ExecutorService, ScheduledExecutorService}
import scalaz.stream.time.awakeEvery
import scalaz.concurrent.Strategy
import scala.concurrent.duration._

object Clocks {

  /** Add `now/elapsed`, `now/remaining`, and `uptime` metrics. */
  def instrument(I: Instruments)(
    implicit ES: ExecutorService = Monitoring.defaultPool,
             TS: ScheduledExecutorService = Monitoring.schedulingPool,
             t: Duration = 5 seconds): Unit = {

    val elapsed =
      I.currentElapsed("now/elapsed", s"Time since the last period of ${I.window} ended")

    val remaining =
      I.currentRemaining("now/remaining", s"Time until the next period of ${I.window} begins")

    val uptime = I.uptime("uptime")

    awakeEvery(t)(Strategy.Executor(ES),TS).map { _ =>
      elapsed.set(())
      remaining.set(())
      uptime.set(())
    }.run.runAsync(_ => ())
  }
}
