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

import java.net.URI
import journal.Logger
import scalaz.syntax.apply._
import scalaz.concurrent.{Task,Strategy}
import scalaz.stream.{Process,Sink}
import scalaz.stream.async.mutable.Signal
import scalaz.{-\/,\/,\/-, Either3,Left3,Middle3,Right3}

trait RemoteFlask {
  def flaskTemplate(path: String) =
    LocationTemplate(s"http://@host:@port/$path")

  def command(c: FlaskCommand): Task[Unit]
}

object LoggingRemote extends RemoteFlask {
  private[this] val log = Logger[HttpFlask]

  def command(c: FlaskCommand): Task[Unit] =
    Task.delay {
      log.info("LoggingRemote recieved: " + c)
    }
}

class HttpFlask(http: dispatch.Http) extends RemoteFlask {
  import FlaskCommand._
  import metrics.{MonitorCommandLatency,UnmonitorCommandLatency}

  private[this] val log = Logger[HttpFlask]

  def command(c: FlaskCommand): Task[Unit] = {
    c match {
      case Monitor(flask, targets) =>
        MonitorCommandLatency.timeTaskSuccess(monitor(flask.location, targets).void)

      case Unmonitor(flask, targets) =>
        UnmonitorCommandLatency.timeTaskSuccess(unmonitor(flask.location, targets).void)
    }
  }

  /**
   * Touch the network and do the I/O using Dispatch.
   */
  private def monitor(to: Location, targets: Set[Target]): Task[String] = {
    import dispatch._, Defaults._
    import argonaut._, Argonaut._
    import JSON.ClustersToJSON

    val payload: Map[ClusterName, List[URI]] =
      targets.groupBy(_.cluster).mapValues(_.map(_.uri).toList)

    val uri = to.uriFromTemplate(flaskTemplate(path = "mirror"))

    val req = Task.delay(url(uri.toString) << payload.toList.asJson.nospaces) <* Task.delay(log.debug(s"submitting to $uri: $payload"))
    req.flatMap(r => fromScalaFuture(http(r OK as.String)))
  }

  /**
   * Touch the network and do the I/O using Dispatch.
   */
  private def unmonitor(to: Location, targets: Set[Target]): Task[String] = {
    import dispatch._, Defaults._
    import argonaut._, Argonaut._
    import JSON.ClustersToJSON

    val payload: Map[ClusterName, List[URI]] =
      targets.groupBy(_.cluster).mapValues(_.map(_.uri).toList)

    val uri = to.uriFromTemplate(flaskTemplate(path = "discard"))

    val req = Task.delay(url(uri.toString) << payload.toList.asJson.nospaces) <* Task.delay(log.debug(s"submitting to $uri: $payload"))
    req.flatMap(r => fromScalaFuture(http(r OK as.String)))
  }

}
