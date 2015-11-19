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


/**
 * This type is just used for syntax, to pick out a default `Key`
 * from a `Historical`, `Continuous`, or `Periodic`.
 * See the instances in [[funnel.DefaultKeys]].
 * For example, given: {{{
 *   val healthy: Metric[Boolean] = for {
 *     n <- reqs.key
 *     db <- dbOk.key
 *     t <- query.key
 *   } yield n < 20000 &&
 *           db &&
 *           t.mean < 20
 * }}}
 *
 * The call to `key` has the signature:
 *
 * `def key[K2](implicit d: DefaultKey[K,K2]): K2`
 *
 * And picks out the default key.
 */
trait DefaultKey[-A,+B] {
  def apply(a: A): B
}

trait DefaultKeys {

  /** Takes the `now` key from the input `Continuous`. */
  implicit def continuous[A]: DefaultKey[Continuous[A], Key[A]] =
    new DefaultKey[Continuous[A], Key[A]] {
      def apply(a: Continuous[A]): Key[A] = a.now
    }

  /** Takes the `now` key from the input `Continuous`. */
  implicit def periodic[A]: DefaultKey[Periodic[A], Key[A]] =
    new DefaultKey[Periodic[A], Key[A]] {
      def apply(a: Periodic[A]): Key[A] = a.sliding
    }
}

object DefaultKey extends DefaultKeys
