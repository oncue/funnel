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
package experimentation

import scala.collection.concurrent.TrieMap

/** A wrapper for instrument constructors to support experimentation and A/B testing */
case class Experimental[I,K](underlying: (Key[K] => Key[K]) => I) {
  val memo = new TrieMap[(ExperimentID, GroupID), I]
  val unadorned = underlying(identity)

  def apply(token: Map[ExperimentID, GroupID])(f: I => Unit): Unit =
    if (token.isEmpty)
      f(unadorned)
    else token.foreach {
      case (e, g) => f(memo.getOrElseUpdate(
        (e, g), underlying(_.setAttribute(AttributeKeys.experimentID, e)
                            .setAttribute(AttributeKeys.experimentGroup, g))))
    }
}

