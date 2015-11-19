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
package integration

import scalaz.Scalaz
import scala.concurrent.duration._
import scalaz.stream.async.boundedQueue
import scalaz.concurrent.{Task,Strategy}
import scalaz.stream.{Process,async,time}
import chemist.{Chemist,PlatformEvent,Pipeline,sinks}

class IntegrationChemist extends Chemist[IntegrationPlatform]{
  import Scalaz._, PlatformEvent._, Pipeline.contextualise
  import Chemist.Flow

  private[this] val log = journal.Logger[IntegrationChemist]

  val lifecycle: Flow[PlatformEvent] =
    Process.emitAll(NoOp :: Nil).map(contextualise)

  val queue =
    boundedQueue[PlatformEvent](100)(Chemist.defaultExecutor)

  val init: ChemistK[Unit] =
    for {
      cfg <- config
      _    = log.info("Initilizing Chemist....")
      _   <- Pipeline.task(
            lifecycle,
            cfg.rediscoveryInterval
          )(cfg.discovery,
            queue,
            cfg.sharder,
            cfg.http,
            cfg.state,
            sinks.unsafeNetworkIO(cfg.remoteFlask, queue)
          ).liftKleisli
    } yield ()
}
