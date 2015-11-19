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

import scalaz._

sealed trait OneOrThree[A] {
  def toList: List[A] = this match {
    case One(a) => List(a)
    case Three(a,b,c) => List(a,b,c)
  }
  def map[B](f: A => B): OneOrThree[B] = this match {
    case One(a) => One(f(a))
    case Three(a,b,c) => Three(f(a),f(b),f(c))
  }
  def map2[B,C](b: OneOrThree[B])(f: (A, B) => C): OneOrThree[C] =
    (this, b) match {
      case (One(a), One(b)) => One(f(a, b))
      case (One(a), Three(b1,b2,b3)) => Three(f(a, b1), f(a, b2), f(a, b3))
      case (Three(a1,a2,a3), One(b)) => Three(f(a1, b), f(a2, b), f(a3, b))
      case (Three(a1, a2, a3), Three(b1, b2, b3)) => Three(f(a1, b1), f(a2, b2), f(a3, b3))
    }
  def ap[B](f: OneOrThree[A => B]): OneOrThree[B] = map2(f)((a,b) => b(a))
}
case class One[A](a: A) extends OneOrThree[A]
case class Three[A](one: A, two: A, three: A) extends OneOrThree[A]

object OneOrThree {
  implicit def instance: Applicative[OneOrThree] =
    new Applicative[OneOrThree] {
      def point[A](a: => A) = One(a)
      def ap[A,B](fa: => OneOrThree[A])(ff: => OneOrThree[A => B]) = fa ap ff
    }

  implicit def continuousToOne[A](c: Continuous[A]): OneOrThree[Key[A]] = One(c.now)
  implicit def periodicToThree[A](p: Periodic[A]): OneOrThree[Key[A]] = Three(p.now, p.previous, p.sliding)
}

