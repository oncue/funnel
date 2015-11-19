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

case class Triple[+A](one: A, two: A, three: A) {
  def map[B](f: A => B): Triple[B] =
    Triple(f(one), f(two), f(three))
  def zip[B](bs: Triple[B]): Triple[(A,B)] =
    Triple((one, bs.one), (two, bs.two), (three, bs.three))
  def zipWith[B,C](bs: Triple[B])(f: (A, B) => C): Triple[C] =
    zip(bs).map(f.tupled)
  def toList: List[A] = List(one, two, three)
}
