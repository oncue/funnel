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
package elastic

import argonaut._
import knobs.IORef
import java.io.File
import instruments._
import journal.Logger
import java.util.Date
import java.text.SimpleDateFormat
import scala.util.control.NonFatal
import scalaz.stream.{async,time,wye}
import scalaz.concurrent.{Task,Strategy}
import scalaz.stream.Process, Process.constant
import scala.concurrent.{Future,ExecutionContext}
import scalaz.{\/,Kleisli,Monad,~>,Reader,Nondeterminism}

object Elastic {
  import Argonaut._
  import Kleisli.ask
  import scalaz.syntax.kleisli._
  import scalaz.syntax.monad._ // pulls in *>
  import concurrent.duration._
  import metrics._

  type SourceURL = String
  type ExperimentID = String
  type GroupID = String
  type Window = String
  type Path = List[String]
  type ES[A] = Kleisli[Task, ElasticCfg, A]

  private[this] val log = Logger[Elastic.type]

  /**
   * Given a scala future, convert it to task when the future completes
   */
  def fromScalaFuture[A](a: => Future[A])(implicit e: ExecutionContext): Task[A] =
    Task async { k =>
      a.onComplete {
        t => k(\/.fromTryCatchNonFatal[A](t.get)) }}

  /**
   *
   */
  val getConfig: ES[ElasticCfg] = ask[Task, ElasticCfg]

  /**
   * Not in Scalaz until 7.2 so duplicating here.
   */
  def lower[M[_]:Monad,A,B](k: Kleisli[M,A,B]): Kleisli[M,A,M[B]] =
    Kleisli(a => Monad[M].pure(k(a)))

  /**
   * natural transformation between task and ES, which is a kleisli
   * in Task. provided for explicitness, instead of using the `liftKleisli`
   * syntax all through the code.
   */
  def lift: Task ~> ES = new (Task ~> ES) {
    def apply[A](t: Task[A]) = t.liftKleisli
  }

  /**
   * get a handle on the subscriptionTimeout without having an ES instance
   */
  def duration: Reader[ElasticCfg, FiniteDuration] =
    Reader { es => es.subscriptionTimeout }

  /****************************** http i/o ******************************/

  import dispatch._, Defaults._

  /**
   * sadly required as dispatch has a very naïve error handler by default,
   * and in this case we're looking to output the body of the response to the log
   * in order to help with debugging in the event documents were not able to be
   * submitted to the backend. Should function exactly like the default impl
   * with the addition of the logging.
   */
  val handler = new FunctionHandler[String]({ resp =>
    val status = resp.getStatusCode
    if((status / 100) == 2) resp.getResponseBody
    else {
      log.error(s"Backend returned a code ${resp.getStatusCode} failure. Body response was: ${resp.getResponseBody}")
      throw StatusCode(status)
    }
  })

  /**
   * given a request, and a payload, compresses the payload into a single-line
   * string and POSTs it to elastic search.
   */
  def elasticJson(req: Req, json: Json): ES[Unit] =
    elastic(req, json.nospaces)

  /**
   * build a function that sends string-like inputs to the specified request
   * using HTTP GET verb which will be handled using the customised `handler`.
   *
   * @see funnel.elastic.Elastic.handler
   */
  def elasticString(req: Req): ES[String] = {
    getConfig.flatMapK(c => fromScalaFuture(c.http(req > handler)))
  }

  /**
   * send the supplied json string to the configured elastic search endpoint
   * using HTTP POST.
   */
  def elastic(req: Req, json: String): ES[Unit] = for {
    es <- getConfig
    ta <- lower(elasticString(req << json))
    _  <- lift(ta.attempt.map(_.fold(
      e => {log.error(s"Unable to send document to elastic search due to '$e'.")
            log.error(s"Configuration was $es. Document was: \n ${json}")},
      _ => ())))
  } yield ()

  /**
   * retries any non-HTTP errors with exponential backoff
   */
  def retry[A](task: Task[A]): Task[A] = {
    val schedule = Stream.iterate(2)(_ * 2).take(30).map(_.seconds)
    val t = task.attempt.flatMap(_.fold(
      e => e match {
        case StatusCode(digits) =>
          Task.delay {
            val r: Int = digits / 100
            if(r == 5) HttpResponse5xx.increment
            else if (r == 4) HttpResponse4xx.increment
          }.flatMap(_ => Task.fail(e))
        case _ =>
          Task.delay {
            log.error(s"Error contacting ElasticSearch: ${e}.\nRetrying...")
            NonHttpErrors.increment
          } >>= (_ => Task.fail(e))
      },
      a => Task.now(a)
    ))
    t.retry(schedule, (e: Throwable) => e.getCause match {
      case StatusCode(code) => false
      case NonFatal(_) => true
      case _ => false
    })
  }

  /**
   * Publishes to an ElasticSearch URL at `esURL`.
   */
  def bufferAndPublish(
    flaskName: String,
    flaskCluster: String
  )(M: Monitoring, E: Strategy
  )(jsonStream: ElasticCfg => Process[Task, Json]): ES[Unit] = {
    // val E = Executor(Monitoring.defaultPool)
    def doPublish(de: Process[Task, Json], cfg: ElasticCfg): Process[Task, Unit] =
      de to (constant((json: Json) => for {
        r <- Task.delay(esURL(cfg))
        _ <- retry( HttpResponse2xx.timeTask(elasticJson(r.POST, json)(cfg)) )
      } yield ()))
    for {
      cfg <- getConfig
      _ = log.info(s"Ensuring Elastic template ${cfg.templateName} exists...")
      _   <- ensureTemplate
      _ = log.info(s"Initializing Elastic buffer of size ${cfg.bufferSize}...")
      buffer = async.circularBuffer[Json](cfg.bufferSize)(E)
      ref <- lift(IORef(Set[Key[Any]]()))
      d   <- duration.lift[Task]
      _ = log.info(s"Started Elastic subscription")
      // Reads from the monitoring instance and posts to the publishing queue
      read = jsonStream(cfg).to(buffer.enqueue)
      // Reads from the publishing queue and writes to ElasticSearch
      write  = doPublish(buffer.dequeue, cfg)
      _ <- lift(Nondeterminism[Task].reduceUnordered[Unit, Unit](Seq(read.run, write.run)))
    } yield ()
  }

  /****************************** indexing ******************************/

  /**
   * construct the actual url to send documents to based on the configuration
   * paramaters, taking into account the index data pattern and prefix.
   */
  def indexURL: Reader[ElasticCfg, Req] = Reader { es =>
    val date = new SimpleDateFormat(es.dateFormat).format(new Date)
    url(s"${es.url}/${es.indexName}-$date")
  }

  /**
   * given the ES url, make sure that all our requests are properly set with
   * the application/json content mime type.
   */
  def esURL: Reader[ElasticCfg, Req] = Reader { es =>
    (indexURL(es) / es.typeName).setContentType("application/json", "UTF-8")
  }

  /**
   * returns true if the index was created. False if it already existed.
   */
  def ensureIndex(url: Req): ES[Boolean] =
    ensureExists(url, createIndex(url))

  /**
   * ensure the index we are trying to send documents too (defined in the config)
   * exists in the backend elastic search cluster.
   */
  def ensureExists(url: Req, action: ES[Unit]): ES[Boolean] = for {
    s   <- elasticString(url.HEAD).mapK(_.attempt)
    b   <- s.fold(
             e => e.getCause match {
               case StatusCode(404) => action *> lift(Task.now(true))
               case _ => lift(Task.fail(e)) // SPLODE!
             },
             _ => lift(Task.now(false)))
  } yield b

  /**
   * create an index based on the
   */
  def createIndex(url: Req): ES[Unit] =
    for {
      _ <- elasticJson(url.PUT, Json("settings" := Json("index.cache.query.enable" := true)))
    } yield ()

  /**
   * load the template specified in the configuration and send it to the index
   * to ensure that all the document fields we publish are correctly handled by
   * elastic search / kibana.
   */
  def ensureTemplate: ES[Unit] = for {
    cfg <- getConfig
    template <- Task.delay(
      cfg.templateLocation.map(f => scala.io.Source.fromFile(new File(f).getAbsolutePath)) getOrElse
        sys.error("no index mapping template specified.")).liftKleisli
    json <- Task.delay(template.mkString).liftKleisli
    turl = url(s"${cfg.url}") / "_template" / cfg.templateName
    _ <- ensureExists(turl, elastic(turl.PUT, json))
  } yield ()

}
