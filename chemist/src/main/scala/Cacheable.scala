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
import Chemist.Context
import scalaz.concurrent.Task

/**
 * a convenience typeclass that allows us to re-use the same
 * caching sink without having to specilize on how we cache
 * various different types at different stages in the pipeline.
 */
trait Cacheable[A]{
  def cache(a: Context[A], to: StateCache): Task[Unit]
}
object Cacheable {
  import scalaz.syntax.apply._
  import Sharding.Distribution

  private[this] val log = Logger[Cacheable.type]

  implicit val planCachable: Cacheable[Plan] =
    new Cacheable[Plan]{
      /**
       * in this case we only want to update the state cache in the
       * event that there is something useful held inside. empty
       * distributions will commonly be seen on the stream due to the
       * use of `contextualise` lifting the values into `Context[A]`,
       * and we don't want that implementation detail screwing with
       * the cached view of the world.
       */
      def cache(in: Context[Plan], to: StateCache): Task[Unit] =
        in match {
          case Context(d, p) if d.isEmpty => // no unapply for distribution
            to.plan(in.value)
          case Context(d, p) =>
            for {
              _ <- to.plan(in.value)
              _  = log.debug(s"value = ${in.value}")
              _  = log.debug(s"distribution = ${in.distribution}")
              _ <- to.distribution(in.distribution)
            } yield ()
        }
    }

  implicit val eventCachable: Cacheable[PlatformEvent] =
    new Cacheable[PlatformEvent]{
      import PlatformEvent._
      /* ignore all the NoOp events here */
      def cache(in: Context[PlatformEvent], to: StateCache): Task[Unit] =
        in match {
          case Context(_, NoOp) => Task.delay(())
          case _                => to.event(in.value)
        }
    }
}
