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
package http

import com.sun.net.httpserver.{HttpExchange,HttpHandler,HttpServer}
import java.io.{BufferedWriter, IOException, OutputStream, OutputStreamWriter}
import java.net.{InetSocketAddress, URL, URI}
import scala.concurrent.duration._
import scalaz.concurrent.{Strategy, Task}
import scalaz.stream._
import scalaz.stream.async.mutable.Signal
import scalaz.stream.async.signal
import Events.Event

object MonitoringServer {

  def defaultRetries = Events.takeEvery(30.seconds, 6)

  /**
   * `/`: self-describing list of available resources
   * `/keys`: stream of changing list of keys
   * `/now`: snapshot of all metrics whose labels begin with 'now'
   * `/previous`: snapshot of all metrics whose labels begin with 'previous'
   * `/sliding`: snapshot of all metrics whose labels begin with 'sliding'
   * `/<prefix>`: snapshot of all metrics whose labels begin with 'prefix' (except for 'stream' and 'keys', which are reserved)
   * `/stream`: stream of all metrics
   * `/stream/keys`: stream of changing list of keys
   * `/stream/<keyid>`: stream of metrics for the given key
   * `/stream/<prefix>`: stream of metrics whose labels start with 'prefix'
   */
  def start(M: Monitoring, port: Int = 8080, keyTTL: Duration = 36.hours): MonitoringServer = {
    val svr = (new MonitoringServer(M, port, keyTTL))
    svr.start()
    svr
  }
}

class MonitoringServer(M: Monitoring, port: Int, keyTTL: Duration = 36.hours) {
  import MonitoringServer._

  private val server = HttpServer.create(new InetSocketAddress(port), 0)

  def start(): Unit = {
    server.setExecutor(Monitoring.serverPool)
    val _ = server.createContext("/", handleMetrics(M))

    server.start()
    M.log.info(s"server started on port: $port")

    M.keySenescence(Events.every(keyTTL), M.distinctKeys).run.runAsync(_.fold(e => {
      M.log.error(s"Asynchronous error starting key senescence: $e - ${e.getMessage}")
      M.log.error(e.getStackTrace.toList.mkString("\n","\t\n",""))
    }, identity _))
    M.log.info(s"Metric key TTL is $keyTTL")

    M.dataDislodgement.run.runAsync(_.fold(e => {
      M.log.error(s"Asynchronous error in data dislodgement process: $e - ${e.getMessage}")
      M.log.error(e.getStackTrace.toList.mkString("\n","\t\n",""))
    }, identity _))
  }

  def stop(): Unit = server.stop(0)

  protected def handleIndex(req: HttpExchange): Unit = {
    flush(200, helpHTML.getBytes, req, "text/html")
  }

  protected def handleStream(M: Monitoring, prefix: String, req: HttpExchange): Unit = {
    req.getResponseHeaders.set("Content-Type", "text/event-stream")
    req.getResponseHeaders.set("Access-Control-Allow-Origin", "*")
    req.sendResponseHeaders(200, 0L) // 0 as length means we're producing a stream
    val events = Monitoring.subscribe(M)(k =>
      Key.StartsWith(prefix)(k) && keyQuery(req.getRequestURI)(k))
    val sink = new BufferedWriter(new OutputStreamWriter(req.getResponseBody))
    SSE.writeEvents(events, sink)
  }

  protected def handleKeys(M: Monitoring, prefix: String, req: HttpExchange): Unit = {
    import JSON._; import argonaut.EncodeJson._
    val query = keyQuery(req.getRequestURI)
    val ks = M.keys.continuous.once.runLastOr(Set.empty).run.filter(x =>
      x.startsWith(prefix) && query(x))
    val respBytes = JSON.prettyEncode(ks).getBytes
    flush(200, respBytes, req)
  }

  protected def handleKeysStream(M: Monitoring, req: HttpExchange): Unit = {
    req.getResponseHeaders.set("Content-Type", "text/event-stream")
    req.getResponseHeaders.set("Access-Control-Allow-Origin", "*")
    req.sendResponseHeaders(200, 0L) // 0 as length means we're producing a stream
    val sink = new BufferedWriter(new OutputStreamWriter(req.getResponseBody))
    SSE.writeKeys(M.distinctKeys.filter(keyQuery(req.getRequestURI)), sink)
  }

  protected def handleNow(M: Monitoring, label: String, req: HttpExchange): Unit = {
    import JSON._; import argonaut.EncodeJson._
    val m = Monitoring.snapshot(M).run
    val respBytes =
      JSON.prettyEncode(m.filterKeys(k =>
        k.startsWith(label) &&
        keyQuery(req.getRequestURI)(k)).values.toList).getBytes
    flush(200, respBytes, req)
  }

  protected def handleAddMirroringURLs(M: Monitoring, req: HttpExchange): Unit = {
    import JSON._; import argonaut.Parse;
    post(req){ json =>
      M.log.debug(s"POST: $json")
      Parse.decodeEither[List[Cluster]](json).fold(
        error => {
          M.log.error(s"Error 400: $error")
          flush(400, error.toString, req)
        },
        blist => {
          val cs: List[Command] =
            blist.flatMap(b => b.urls.map(u => Mirror(new URI(u), b.label)))

          M.log.debug(s"recieved instruction to mirror '${cs.mkString(",")}'")

          val p0: Process[Task, Command] = Process.emitAll(cs)
          val p: Process[Task, Unit] = p0.to(M.mirroringQueue.enqueue)
          p.run.run
          M.log.debug(s"added to mirroring queue")
          flush(202, Array.empty[Byte], req)
        }
      )
    }
  }

  protected def handleHaltMirroringURLs(M: Monitoring, req: HttpExchange): Unit = {
    import JSON._; import argonaut.Parse;

    post(req){ json =>
      Parse.decodeEither[List[String]](json).fold(
        error => flush(400, error.toString, req),
        list => {
          val p0: Process[Task, Command] = Process.emitAll(list.map(u => Discard(new URI(u))))
          val p = p0 to M.mirroringQueue.enqueue
          p.run.run

          flush(202, Array.empty[Byte], req)
        }
      )
    }
  }

  private def handleListMirroringURLs(M: Monitoring, req: HttpExchange): Unit = {
    import JSON._; import argonaut._, Argonaut._;
    flush(200, M.mirroringUrls.map {
      case (a,b) => Cluster(a,b)
    }.asJson.nospaces.getBytes, req)
  }

  private def handleAudit(M: Monitoring, filter: Option[String], req: HttpExchange): Unit = {
    import JSON._; import argonaut._, Argonaut._;
    val result: Task[List[(String,Int)]] = filter.fold(M.auditByPrefix)(M.auditByAttribute)
    result.attemptRun.fold(
      err => flush(500, err.getMessage.toString.getBytes("UTF-8"), req),
      list => flush(200,
        list.map(t => Audit(t._1, t._2)).asJson.nospaces.getBytes, req)
    )
  }

  private def handleVersion(req: HttpExchange): Unit = {
    import argonaut._, Argonaut._
    flush(200, Json(
      "version" -> jString(BuildInfo.version),
      "git-revision" -> jString(BuildInfo.gitRevision)).nospaces.getBytes, req)
  }

  private def post(req: HttpExchange)(f: String => Unit): Unit = {
    import scala.io.Source
    if(req.getRequestMethod.toLowerCase == "post"){
      // as the payloads here will be small, lets just turn it into a string
      val json = Source.fromInputStream(req.getRequestBody).mkString
      f(json)
    } else {
      M.log.error("405 - Request method not allowed.")
      flush(405, "Request method not allowed.", req)
    }
  }

  private def flush(status: Int, body: String, req: HttpExchange): Unit =
    flush(status, body.getBytes, req)

  private def flush(status: Int, body: Array[Byte], req: HttpExchange, contentType: String = "application/json"): Unit = {
    req.getResponseHeaders.set("Content-Type", contentType)
    req.getResponseHeaders.set("Access-Control-Allow-Origin", "*")
    req.sendResponseHeaders(status,body.length)
    req.getResponseBody.write(body)
  }

  import scalaz.syntax.traverse._, scalaz.std.list._, scalaz.std.option._

  def getQuery(uri: URI): Map[String, String] =
    Option(uri.getQuery).flatMap(_.split("&").toList.traverse { x =>
      x.split("=") match {
        case Array(k, v) => Some((k, v))
        case _ => None
      }
    }).map(_.toMap).getOrElse(Map())

  def keyQuery(uri: URI): Key[Any] => Boolean = k => {
    import JSON._; import argonaut._, Argonaut._
    val q = getQuery(uri)
    def attr[T:DecodeJson](a: String, v: T) =
      q.get(a).flatMap(Parse.decodeOption[T]).map(_ == v).getOrElse(true)
    val p = attr("units", k.units) && attr("type", k.typeOf)(DecodeReportableT)
    (q - "units" - "type").foldLeft(p) {
      case (p, (a, v)) => p && (k.attributes.get(a) == Some(v))
    }
  }

  protected def handleMetrics(M: Monitoring) = new HttpHandler {
    def handle(req: HttpExchange): Unit = try {
      M.log.info("requested path: " + req.getRequestURI.getPath)
      val path = req.getRequestURI.getPath match {
        case "/" => Nil
        case p   => p.split("/").toList.tail
      }
      path match {
        case Nil                           => handleIndex(req)
        case "version" :: Nil              => handleVersion(req)
        case "audit"   :: Nil              => handleAudit(M, None, req)
        case "audit"   :: attr :: Nil      => handleAudit(M, Option(attr), req)
        case "halt"    :: Nil              => handleHaltMirroringURLs(M, req)
        case "mirror"  :: Nil              => handleAddMirroringURLs(M, req)
        case "mirror"  :: "sources" :: Nil => handleListMirroringURLs(M, req)
        case "keys"    :: tl               => handleKeys(M, tl.mkString("/"), req)
        case "stream"  :: "keys" :: Nil    => handleKeysStream(M, req)
        case "stream"  :: tl               => handleStream(M, tl.mkString("/"), req)
        case now                           => handleNow(M, now.mkString("/"), req)
      }
    }
    catch {
      case e: Exception => M.log.error("fatal error: " + e)
    }
    finally req.close
  }

  val helpHTML = s"""
    |<!DOCTYPE html>
    |<html lang="en">
    |  <head>
    |    <link rel="stylesheet" href="//maxcdn.bootstrapcdn.com/bootstrap/3.2.0/css/bootstrap.min.css">
    |    <link rel="stylesheet" href="//maxcdn.bootstrapcdn.com/bootstrap/3.2.0/css/bootstrap-theme.min.css">
    |    <script src="//maxcdn.bootstrapcdn.com/bootstrap/3.2.0/js/bootstrap.min.js"></script>
    |    <title>Funnel &middot; ${BuildInfo.version} &middot; ${BuildInfo.gitRevision}</title>
    |    <style type="text/css">
    |    /* Space out content a bit */
    |    body {
    |      padding-top: 20px;
    |      padding-bottom: 20px;
    |    }
    |
    |    /* Everything but the jumbotron gets side spacing for mobile first views */
    |    .header,
    |    .marketing,
    |    .footer {
    |      padding-right: 15px;
    |      padding-left: 15px;
    |    }
    |
    |    /* Custom page header */
    |    .header {
    |      border-bottom: 1px solid #e5e5e5;
    |    }
    |    /* Make the masthead heading the same height as the navigation */
    |    .header h3 {
    |      padding-bottom: 19px;
    |      margin-top: 0;
    |      margin-bottom: 0;
    |      line-height: 40px;
    |    }
    |
    |    /* Custom page footer */
    |    .footer {
    |      padding-top: 19px;
    |      color: #777;
    |      border-top: 1px solid #e5e5e5;
    |    }
    |
    |    /* Customize container */
    |    @media (min-width: 768px) {
    |      .container {
    |        max-width: 730px;
    |      }
    |    }
    |    .container-narrow > hr {
    |      margin: 30px 0;
    |    }
    |
    |    /* Main marketing message and sign up button */
    |    .jumbotron {
    |      text-align: center;
    |      border-bottom: 1px solid #e5e5e5;
    |    }
    |    .jumbotron .btn {
    |      padding: 14px 24px;
    |      font-size: 21px;
    |    }
    |
    |    /* Supporting marketing content */
    |    .marketing {
    |      margin: 40px 0;
    |    }
    |    .marketing p + h4 {
    |      margin-top: 28px;
    |    }
    |
    |    /* Responsive: Portrait tablets and up */
    |    @media screen and (min-width: 768px) {
    |      /* Remove the padding we set earlier */
    |      .header,
    |      .marketing,
    |      .footer {
    |        padding-right: 0;
    |        padding-left: 0;
    |      }
    |      /* Space out the masthead */
    |      .header {
    |        margin-bottom: 30px;
    |      }
    |      /* Remove the bottom border on the jumbotron for visual effect */
    |      .jumbotron {
    |        border-bottom: 0;
    |      }
    |    }
    |    </style>
    |  </head>
    |
    |  <body>
    |
    |    <div class="container">
    |      <div class="header">
    |        <ul class="nav nav-pills pull-right">
    |          <li><a href="https://github.svc.oncue.com/pages/IntelMedia/funnel/">About</a></li>
    |          <li><a href="mailto:timothy.m.perrett@oncue.com">Contact</a></li>
    |        </ul>
    |        <h3 class="text-muted">Funnel Control Panel</h3>
    |      </div>
    |
    |      <div class="row marketing">
    |        <div class="col-lg-6">
    |          <h4>Metric Resources</h4>
    |          <p><a href="/keys">GET /keys</a>: Display the current snapshot of all keys registred with the monitoring instance.</p>
    |          <p><a href="/keys/prefix">GET /keys/prefix</a>: Display the current snapshot of all keys prefixed by the word 'prefix'.</p>
    |
    |          <h4>Window Resources</h4>
    |          <p><a href="/now">GET /now</a>: Current values for all metrics prefixed by 'now'.</p>
    |          <p><a href="/previous">GET /previous</a>: Current values for all metrics prefixed by 'previous'.</p>
    |          <p><a href="/sliding">GET /sliding</a>: Current values for all metrics prefixed by 'sliding'.</p>
    |        </div>
    |
    |        <div class="col-lg-6">
    |          <h4>Operations Resources</h4>
    |          <p><a href="/mirror">POST /now</a>: Dynamically mirror metrics from other funnel(s).</p>
    |          <p><a href="/halt">POST /halt</a>: Stop mirroring metrics from the given funnel URLs.</p>
    |          <p><a href="/audit">GET /audit</a>: Display an aggregated view of all keys in this server broken down by previx.</p>
    |          <p><a href="/audit">GET /audit/:attribute</a>: Display an aggregated view of all keys in this server broken down by attribute key.</p>
    |          <p><a href="/mirror/sources">GET /mirror/sources</a>: If mirroring from other nodes, display the sources of those keys.</p>
    |        </div>
    |      </div>
    |
    |      <div class="footer">
    |        <p>&copy; Verizon OnCue 2014</p>
    |      </div>
    |
    |    </div>
    |  </body>
    |</html>
  """.stripMargin

}
