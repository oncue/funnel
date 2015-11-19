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

import unfiltered.request._
import unfiltered.response._
import unfiltered.netty._
import argonaut._, Argonaut._
import scalaz.concurrent.Task
import scalaz.stream.Process
import scalaz.{\/,-\/,\/-}
import journal.Logger
import concurrent.duration._

object JsonRequest {
  def apply[T](r: HttpRequest[T]) =
    new ParseWrap(r, new Parse[HttpRequest[T]] {
      def parse(req: HttpRequest[T]) = JsonParser.parse(Body.string(req))
    })
}

object JsonResponse {
  def apply[A: EncodeJson](a: A, params: PrettyParams = PrettyParams.nospace) =
    JsonContent ~>
      ResponseString(a.jencode.pretty(params))
}

object Server {
  private val log = Logger[Server.type]

  // there seems to be a bug in Task that makes doing what we previously had here
  // not possible. The server gets into a hang/deadlock situation.
  def unsafeStart[U <: Platform](server: Server[U]): Unit = {
    import server.{platform,chemist}
    val disco   = platform.config.discovery
    val sharder = platform.config.sharder

    // do the ASCII art
    log.info(Banner.text)

    chemist.init(platform).runAsync {
      case -\/(err) =>
        log.error(s"FATAL: Unable to initilize the chemist service. Failed with error: $err")
        err.printStackTrace

      case \/-(_) => log.error("FATAL: Sucsessfully initilized chemist at startup.")
    }

    val p = this.getClass.getResource("/oncue/www/")
    log.info(s"Setting web resource path to '$p'")

    unfiltered.netty.Server
      .http(platform.config.network.port, platform.config.network.host)
      .resources(p, cacheSeconds = 3600)
      .handler(server)
      .run
  }
}

@io.netty.channel.ChannelHandler.Sharable
class Server[U <: Platform](val chemist: Chemist[U], val platform: U) extends cycle.Plan with cycle.SynchronousExecution with ServerErrorResponse {
  import chemist.ChemistK
  import JSON._
  import Server._
  import metrics._

  protected def json[A : EncodeJson](a: ChemistK[A]) =
    a(platform).attemptRun.fold(
      e => {
        log.error(s"Unable to process response: ${e.toString} - ${e.getMessage}")
        e.printStackTrace
        InternalServerError ~> JsonResponse(e.toString)
      },
      o => Ok ~> JsonResponse(o))

  protected def decode[A : DecodeJson](req: HttpRequest[Any])(f: A => ResponseFunction[Any]) =
    JsonRequest(req).decodeEither[A].map(f).fold(fail => BadRequest ~> ResponseString(fail), identity)

  def intent: cycle.Plan.Intent = {
    case GET(Path("/")) =>
      Redirect("index.html")

    case GET(Path("/status")) =>
      Ok ~> JsonResponse(Chemist.version)

    case GET(Path("/distribution")) =>
      json(chemist.distribution.map(_.toList))

    case GET(Path("/unmonitorable")) =>
      json(chemist.listUnmonitorableTargets)

    case GET(Path("/platform/history")) =>
      json(chemist.platformHistory.map(_.toList))

    case POST(Path("/distribute")) =>
      NotImplemented ~> JsonResponse("This feature is not avalible in this build. Sorry :-)")

    case GET(Path(Seg("shards" :: Nil))) =>
      json(chemist.shards)

    case GET(Path(Seg("shards" :: id :: Nil))) =>
      json(chemist.shard(FlaskID(id)))

    case GET(Path(Seg("shards" :: id :: "sources" :: Nil))) =>
      import http.JSON._	// For Cluster codec
      json(chemist.sources(FlaskID(id)))
  }
}
