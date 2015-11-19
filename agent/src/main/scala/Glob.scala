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
/**********************************************************************************************\
* Rapture Command Line Interface Library                                                       *
* Version 0.9.0                                                                                *
*                                                                                              *
* The primary distribution site is                                                             *
*                                                                                              *
*   http://rapture.io/                                                                         *
*                                                                                              *
* Copyright 2010-2014 Jon Pretty, Propensive Ltd.                                              *
*                                                                                              *
* Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file    *
* except in compliance with the License. You may obtain a copy of the License at               *
*                                                                                              *
*   http://www.apache.org/licenses/LICENSE-2.0                                                 *
*                                                                                              *
* Unless required by applicable law or agreed to in writing, software distributed under the    *
* License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,    *
* either express or implied. See the License for the specific language governing permissions   *
* and limitations under the License.                                                           *
\**********************************************************************************************/
package funnel
package agent

import java.util.regex._

/**
 * This in an incomplete implementation as it does not support character classes enclosed
 * by `[` and `]`.
 *
 * Borrowed from rapture.io here:
 * https://github.com/propensive/rapture-cli/blob/master/src/glob.scala
 */
case class Glob(glob: String){
  lazy val pattern: Pattern = {
    val sb = new StringBuilder
    var start = true
    glob foreach { c =>
      start = false
      sb.append(c match {
        case '*' => if(start) "[^./][^/]*" else "[^/]*"
        case '?' => if(start) "[^./][^/]*" else "[^/]*"
        case '/' => start = true; "/"
        case esc@('.' | '[' | '{' | '(' | '+' | '^' | '$' | '|') => "\\"+esc
        case other => other.toString
      })
    }
    Pattern.compile(sb.toString)
  }
  def matches(s: String): Boolean =
    pattern.matcher(s).matches
}
