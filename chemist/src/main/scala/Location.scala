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

import scalaz.{\/,-\/,\/-}
import java.net.URI
import LocationIntent._

/**
 * Represents the combination of scheme, host and port
 * as a single unit. It is entirely feasible that a single
 * machine host has multiple `Location` instances. Moreover,
 * a `Location` has a minimum of one URI, which represents
 * the canonical network location of this specific scheme/host/port
 * which can be used for network validation etc. In addition,
 * a `Location` might have a selection of templated path URIs, but
 * this is entirely optional.
 */
case class Location(
  host: String,
  port: Int,
  datacenter: String,
  protocol: NetworkScheme = NetworkScheme.Http,
  intent: LocationIntent,
  templates: Seq[LocationTemplate]
){

  /**
   * This is used to generate the paths that we need to monitor
   * based on the supplied templates. For example, even though
   * the location knows its IP:PORT combination, it does not know
   * which paths we might like to monitor - let's say we wanted
   * /stream/previous and /stream/now?kind=traffic; these are clearly
   * different URIs, so we use the templates to generate that.
   */
  def templatedPathURIs: Seq[URI] =
    templates.map(uriFromTemplate)

  /**
   * Generate a canonical URI for this particular location. This is
   * always done without regard for any templates, as its typically
   * used for network validation.
   */
  def uri: URI =
    URI.create(s"${protocol.toString}://$host:$port")

  /**
   * Given a particular `LocationTemplate`, apply this `Location`s
   * data to the template. This is used when we have an arbitrary
   * template and want to turn it into something actionable elsewhere
   * in the codebase.
   */
  def uriFromTemplate(t: LocationTemplate): URI =
    URI.create(t.build(
      "@protocol" -> protocol.scheme,
      "@host"     -> host,
      "@port"     -> port.toString
    ))
}

object Location {

  /**
   * Given a `java.net.URI`, attempt to conver this into a `Location`
   * instance. This is not guarenteed to work, and there are a range
   * of failure cases because of the problems with the way `URI` is
   * actually implemented within the JDK. With that being said, for
   * our purposes, this works as our URIs are always well formed -
   * if they are not, we either have a problem with the configuration
   * (i.e. a human screwed up the settings) or LocationTemplate is
   * doing the wrong thing.
   */
  def fromURI(
    uri: URI,
    dc: String,
    int: LocationIntent,
    tmp: Map[NetworkScheme, Seq[LocationTemplate]]
  ): Option[Location] =
    for {
      a <- Option(uri.getHost)
      b <- Option(uri.getPort) if b > -1
      c <- NetworkScheme.fromString(uri.getScheme)
      d <- tmp.get(c).orElse(Some(Seq.empty[LocationTemplate]))
    } yield Location(
      host = a,
      port = b,
      protocol = c,
      datacenter = dc,
      intent = int,
      templates = d)
}
