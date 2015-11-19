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

import java.util.Deque
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.locks.ReentrantLock
import scalaz.concurrent.Task

case class BoundedStack[A](maximumEntries: Int){
  import scala.collection.JavaConverters._

  private val list: Deque[A] = new ConcurrentLinkedDeque[A]
  private val lock = new Object

  def push(item: A): Task[Unit] = {
    def add: Unit = {
      list.push(item)
      if (list.size > maximumEntries) { val _ = list.removeLast() }
    }

    Task.delay(lock.synchronized(add))
  }

  def pop: Option[A] = Option(list.poll)
  def peek: Option[A] = Option(list.peekFirst)
  def isEmpty: Boolean = list.isEmpty
  def size: Int = list.size
  def toSeq: Seq[A] = list.iterator.asScala.toSeq

  assert(maximumEntries > 0, "The maximum number of entries must greater than zero.")
}
