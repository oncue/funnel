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

import scalaz.concurrent.Task
import scalaz._, Scalaz._
import org.scalatest.{FlatSpec,Matchers}

class BoundedStackSpec extends FlatSpec with Matchers {
  val alpha = ('A' to 'Z').toList

  it should "pop values in order they were pushed" in {
    val B1 = new BoundedStack[Char](26)
    alpha.traverse(B1.push).run

    alpha.foldLeft(List.empty[Char]){ (a, c) =>
      B1.pop.toList ++ a
    } should equal (alpha)
  }

  it should "pop the last whcih was pushed" in {
    val B1 = new BoundedStack[Char](26)
    alpha.traverse(B1.push).run

    B1.pop should equal(Some('Z')) // last pushed, first to pop
    B1.peek should equal(Some('Y'))
    B1.pop should equal(Some('Y'))
    B1.push('A').run
    B1.pop should equal(Some('A'))
  }

  it should "honour the maximum number of entries" in {
    val B1 = new BoundedStack[Int](10)
    (1 to 11).toVector.traverse(B1.push).run
    B1.peek should equal(Some(11))
    B1.toSeq should equal ((2 to 11).reverse.toSeq)
  }

  it should "work reasonably even with a lot of thrashing on the stack" in {
    val B1 = new BoundedStack[Int](10)
    (1 to 10001).toVector.traverse(i => B1.push(i)).run
    B1.peek should equal(Some(10001))
    B1.toSeq.toList should equal ( (2 to 10001).toList.reverse.take(10) )
  }

}
