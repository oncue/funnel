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
package zeromq

import scalaz.concurrent.Task
import scalaz.\/
import java.net.URI

case class Location(
  uri: URI,
  protocol: Protocol,
  hostOrPath: String
)
object Location {

  private def hostOrPath(uri: URI): Option[String] = {
    val port = Option(uri.getPort).flatMap {
      case -1 => Option.empty[String]
      case o  => Option(o.toString)
    }
    Option(uri.getHost).flatMap(h => port.map(h + ":" + _)
      ) orElse Option(uri.getPath)
  }

  private def go[A](o: Option[A])(errMsg: String): Throwable \/ A =
    \/.fromEither(o.toRight(new RuntimeException(errMsg)))

  def apply(uri: URI): Throwable \/ Location =
    for {
      a <- go(uri.getScheme.split('+').lastOption
            )(s"URI did not have the correct 'zeromq+' scheme prefix. uri = '$uri'")
      b <- go(Protocol.fromString(a)
            )(s"Unable to infer protocol scheme from URI '$uri'")
      c <- go(hostOrPath(uri)
            )("URI contained no discernible host:port or path.")
    } yield Location(URI.create(s"$a:${uri.getSchemeSpecificPart}"), b, c)
}
