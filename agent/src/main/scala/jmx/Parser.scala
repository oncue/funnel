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
package agent
package jmx

import scalaz.\/

object Parser {

  def parse(cn: String, at: String) =
    fromCanonicalName(cn).map(_ + "/" + at)

  /**
   * This gnarly replace hack is needed because Java products using
   * Yammer / Coda metric library are incorrectly publishing MBean
   * object names that are quoted on the wire. Whilst the agent could
   * already pick these values up, they get fucked up on the funnel wire
   * as they are quouted in the HTTP path, and thus "not safe".
   */
  def fromCanonicalName(cn: String) = \/.fromTryCatchNonFatal {
    val Array(domain, tail) = cn.split(':')
    (formatDomain(domain) + "/" +
    keyspaces(tail).map(_._2).mkString("/")).toLowerCase.replace("\"", "")
  }

  /**
   * This really is not ideal as the elements come in from the order they
   * were actually added to the object name by the creating JMX endpoint,
   * which unhelpfully varies product to product. As such, this will look
   * right sometimes, and be backwards in other cases.
   *
   * //sigh
   */
  private[this] def keyspaces(tail: String): Seq[(String,String)] =
    tail.split(',').reverse.map { part =>
      val Array(key,value) = part.trim.split('=')
      (key,value)
    }

  private[this] def formatDomain(dn: String): String =
    dn.toLowerCase.replace('.','/')

}
