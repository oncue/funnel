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

import journal.Logger
import scalaz.Nondeterminism
import scalaz.concurrent.Task
import scalaz.stream.{Sink,sink}
import scalaz.stream.async.mutable.{Queue,Signal}

/**
 * module containing the process sinks used by chemist.
 * these sinks for the end of the world for the streams
 * and are where all the system effects should be placed.
 */
object sinks {
  import Sharding.Distribution
  import Chemist.Context
  import FlaskCommand._
  import scalaz.syntax.apply._

  private[this] val log = Logger[sinks.type]

  /**
   * create a caching sink from a `Cacheable[A]`. this is intented as
   * a simple convenience in creating sinks. Probally overkill, but
   * you know, whatever.
   */
  def caching[A : Cacheable](to: StateCache): Sink[Task, Context[A]] =
    sink.lift(c => implicitly[Cacheable[A]].cache(c,to))

  /**
   * really only used for testing purposes; doesnt serve any
   * useful function other than for debugging.
   */
  val logging: Sink[Task, Context[Plan]] =
    sink.lift(c => Task.delay(log.debug(s"recieved $c")))

  /**
   * this should really only be used in proudction or integration
   * testing as it will actually reach out on the wire and do the
   * network I/O.
   */
  def unsafeNetworkIO(f: RemoteFlask, q: Queue[PlatformEvent]): Sink[Task, Context[Plan]] = sink.lift {
    case Context(d, Distribute(work)) => {
      val tasks = work.fold(List.empty[Task[Unit]]
        ){ (a,b,c) => c :+ f.command(Monitor(a,b)) }

      Task.delay(log.debug(s"distributing targets to flasks. work = $work")) <*
      Nondeterminism[Task].gatherUnordered(tasks)
    }

    case Context(d, p@Redistribute(stop, start)) => {
      val stopping = stop.fold(List.empty[Task[Unit]]
        ){ (a,b,c) => c :+ f.command(Unmonitor(a,b)) }

      val starting = start.fold(List.empty[Task[Unit]]
        ){ (a,b,c) => c :+ f.command(Monitor(a,b)) }

      Task.delay(log.info(s"recieved redistribute command: $p")) <*
      Nondeterminism[Task].gatherUnordered(stopping) <*
      Nondeterminism[Task].gatherUnordered(starting)
    }

    case Context(d, Produce(task)) =>
      Task.delay(log.debug("pushing synthetic platform events onto our own queue")) <*
      task.flatMap(q.enqueueAll(_))

    case Context(d, a) =>
      Task.delay(log.debug(s"nothing to see here but $a"))
  }
}
