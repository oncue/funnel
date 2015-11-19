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

import scala.concurrent.duration._
import scalaz._
import scalaz.concurrent.Task
import scalaz.std.tuple._

import Events._

object Metric {
  /** Infix syntax for `Metric`. */
  implicit class MetricOps[A](self: Metric[A]) {

    /**
     * Publish the `Metric` under a key or set of keys with the given properties.
     * If the `Metric` is composed out of at least one `Periodic(now, previous, sliding)`
     * keyset, this will publish three keys: `now/label`, `previous/label`, and `sliding/label`.
     *
     * The semantics of this are the "obvious" semantics of list multiplication.
     * For two `Continuous` metrics, their composite metric will be a `Continuous` metric as well.
     * For two `Periodic` metrics, their composition will be `Periodic`, combining
     * `now` with `now`, `previous` with `previous`, and `sliding` with `sliding`.
     * For one `Periodic` and one `Continuous`, the `Continuous` one will be combined with
     * each of `now`, `previous`, and `sliding`.
     */
    def publish(label: String,
                units: Units = Units.None,
                description: String = "",
                M: Monitoring = Monitoring.default,
                I: Instruments = instruments.instance,
                keyMod: Key[A] => Key[A] = identity[Key[A]] _)(
                implicit R: Reportable[A]): Task[KeySet[A]] = {
      val keys = I.periodicKeys(label, units, description, keyMod)
      val (ev, oot) = eval(M)
      oot match {
        case One(t) => M.publish(keys.now)(ev)(t).map(_ => One(keys.now))
        case Three(now, prev, sliding) => for {
          _ <- M.publish(keys.now)(ev)(now)
          _ <- M.publish(keys.previous)(ev)(prev)
          _ <- M.publish(keys.sliding)(ev)(sliding)
        } yield Three(keys.now, keys.previous, keys.sliding)
      }
    }

    /**
     * Evaluate the `Metric` using the given `Monitoring` instance.
     * This will return an `Event` (a stream of clock ticks) that fires every time
     * the keys involved in the `Metric` are updated, together with either
     * one or three `Task[A]` that evaluate to the latest value under the keys
     * (either `now` or `{now, previous, sliding}`) for the `Metric`.
     */
    def eval(M: Monitoring = Monitoring.default): (Event, OneOrThree[Task[A]]) =
      self.foldMap[λ[α => (Event,OneOrThree[Task[α]])]](new (KeySet ~> λ[α => (Event,OneOrThree[Task[α]])]) {
        def apply[A](a: KeySet[A]) = a match {
          case One(k) => (changed(k), One(M latest k))
          case Three(now, prev, sliding) =>
            (or(changed(now), or(changed(prev),changed(sliding))),
             Three(M latest now, M latest prev, M latest sliding))
        }
      })(tuple2Monad[Event] compose Applicative[OneOrThree] compose Applicative[Task])


  }

  implicit def toMetric[A](k: Key[A]): Metric[A] =
    FreeAp.lift[KeySet,A](One(k))

  implicit def periodicToMetric[A](k: Periodic[A]): Metric[A] =
    FreeAp.lift[KeySet,A](Three(k.now, k.previous, k.sliding))

  implicit def continuousToMetric[A](k: Continuous[A]): Metric[A] =
    FreeAp.lift[KeySet,A](One(k.now))

}
