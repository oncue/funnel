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
package http

import unfiltered.request._
import unfiltered.response._
import unfiltered.netty._
import argonaut._, Argonaut._
import scalaz.concurrent.Task

object JsonRequest {
  def apply[T](r: HttpRequest[T]) =
    new ParseWrap(r, new Parse[HttpRequest[T]] {
      def parse(req: HttpRequest[T]) = JsonParser.parse(Body.string(req))
    })
}

object JsonResponse {
  def apply[A: EncodeJson](a: A, params: PrettyParams = PrettyParams.nospace) =
    JsonContent ~> ResponseString(a.jencode.pretty(params))
}

@io.netty.channel.ChannelHandler.Sharable
class Server(I: Instruments) extends cycle.Plan with cycle.SynchronousExecution with ServerErrorResponse {
  import JSON._
  import concurrent.duration._
  import instruments._
  import metrics._

  private def decode[A : DecodeJson](req: HttpRequest[Any])(f: A => ResponseFunction[Any]) =
    JsonRequest(req).decodeEither[A].map(f).fold(fail => BadRequest ~> ResponseString(fail), identity)

  def intent = {
    case r@Path("/metrics") => r match {
      case POST(_) =>
        http.MetricLatency.time {
          decode[InstrumentRequest](r){ typed =>
            RemoteInstruments.metricsFromRequest(typed)(I).map { _ =>
              Ok ~> JsonResponse("ok.")
            }.or(Task.now(InternalServerError ~> JsonResponse("failed."))).run
          }
        }
      case _ => BadRequest
    }
  }
}
