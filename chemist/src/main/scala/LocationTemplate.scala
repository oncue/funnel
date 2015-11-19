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

/**
 * This has to be the naievest template system ever
 * constructed, but given its all being read from a
 * config, im not going to worry about parsing or
 * security issues, and instead do blind string
 * replacement like a BAWWWWSSSS.
 */
case class LocationTemplate(template: String){
  def has(n: NetworkScheme): Boolean =
    template.startsWith(n.scheme)

  def build(pairs: (String,String)*): String =
    pairs.toSeq.foldLeft(template){ (a,b) =>
      val (k,v) = b
      a.replace(k,v)
    }
}
