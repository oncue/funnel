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
import java.io.File

import journal.Logger
import java.util.Date
import java.text.SimpleDateFormat
import java.util.concurrent.{ExecutorService, Executors}

import scala.util.control.NonFatal
import scalaz.stream.Process
import scalaz.concurrent.{Strategy, Task}
import Process.constant
import scalaz._
import scalaz.stream.async.mutable.ScalazHack

object Elastic {
  import Argonaut._
  import Kleisli.ask
  import scalaz.syntax.kleisli._
  import scalaz.syntax.monad._ // pulls in *>
  import scala.concurrent.duration._

  type SourceURL = String
  type ExperimentID = String
  type GroupID = String
  type Window = String
  type Path = List[String]
  type ES[A] = Kleisli[Task, ElasticCfg, A]

  private[this] val log = Logger[Elastic.type]

  val esPool: ExecutorService =
    Executors.newFixedThreadPool(8, Monitoring.daemonThreads("elastic-publisher-thread"))

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
  def duration: Reader[ElasticCfg, FiniteDuration] = Reader { es => es.subscriptionTimeout }

  /**
   * build a function that sends string-like inputs to the specified request
   * using HTTP GET verb which will be handled using the customised `handler`.
   *
   * @see funnel.elastic.Elastic.handler
   */
  def elasticString(req: HttpOp)(H: HttpLayer): ES[String] = H.http(req)

  /**
   * send the supplied json string to the configured elastic search endpoint
   * using HTTP POST.
   */
  def elastic(req: HttpOp)(H: HttpLayer): ES[Unit] = for {
    ta <- lower(elasticString(req)(H))
    _  <- lift(ta.attempt.map(_.fold(
      e => log.error(s"Unable to send document to elastic search due to '$e'. Request was: \n $req"),
      _ => ())))
  } yield ()

  /**
   * retries any non-HTTP errors with exponential backoff
   */
  def retry[A](task: Task[A])(m: ElasticMetrics): Task[A] = {
    val schedule = Stream.iterate(2)(_ * 2).take(30).map(_.seconds)
    val t = task.attempt.flatMap(_.fold(
      {
        case e: HttpException =>
          Task.delay {
            if (e.serverError) m.HttpResponse5xx.increment
            else if (e.clientError) m.HttpResponse4xx.increment
          }.flatMap(_ => Task.fail(e))
        case e =>
          Task.delay {
            log.error(s"Error contacting ElasticSearch: $e. Retrying...")
            m.NonHttpErrors.increment
          } >>= (_ => Task.fail(e))
      },
      a => Task.now(a)
    ))
    t.unsafePerformRetry(schedule, {
      case e: HttpException if e.clientError => false
      case e: HttpException => true //retry on server errors like "gateway timeout"
      case e@NonFatal(_) => true
      case _ => false
    })
  }

  private def toBulkPayload(jsons: Seq[Json]): String =
    jsons.map {
      js => s"""{ "create": {}}\n${js.nospaces}\n"""
    }.mkString

  /**
   * Publishes to an ElasticSearch URL at `esURL`.
   */
  def bufferAndPublish(
    flaskName: String,
    flaskCluster: String
  )(M: Monitoring, E: Strategy, H: HttpLayer, iselfie: ElasticMetrics
  )(jsonStream: ElasticCfg => Process[Task, Json]): ES[Unit] = {
    def doPublish(de: Process[Task, Seq[Json]], cfg: ElasticCfg): Process[Task, Unit] =
      de to constant(
        (jsons: Seq[Json]) => retry(iselfie.HttpResponse2xx.timeTask(
          for {
            //delaying task is important here. Otherwise we will not really retry to send http request
            u <- Task.delay(esBulkURL)
            _ = log.debug(s"Posting ${jsons.size} docs.")
            _ <- H.http(POST(u, Reader((cfg: ElasticCfg) => toBulkPayload(jsons))))(cfg)
          } yield ()
        ))(iselfie).attempt.map {
          case \/-(v) => ()
          case -\/(t) =>
            //if we fail to publish metric, proceed to the next one
            // TODO: consider circuit breaker on ES failures (embed into HttpLayer)
            // TODO: how does publishing dies when flasks stops monitoring target? do we release resources?
            log.warn(s"[elastic] failed to publish. error=$t cnt=${jsons.size} data=$jsons")
            iselfie.BufferDropped.increment
            ()
        }
      )

    for {
      _ <- ensureTemplate(H)
      r <- Kleisli.kleisli[Task, ElasticCfg, Unit] {
        (cfg: ElasticCfg) =>
          log.info(s"Initializing Elastic buffer of size ${cfg.bufferSize}...")
          val buffer = ScalazHack.observableCircularBuffer[Json](
            cfg.bufferSize, iselfie.BufferDropped, iselfie.BufferUsed
          )(E)

          log.info(s"Started Elastic subscription (max batch size=${cfg.batchSize})")
          // Reads from the monitoring instance and posts to the publishing queue
          val read = jsonStream(cfg).to(buffer.enqueue)
          // Reads from the publishing queue and writes to ElasticSearch
          val write = doPublish(buffer.dequeueBatch(cfg.batchSize), cfg)
          Nondeterminism[Task].reduceUnordered[Unit, Unit](Seq(read.run, write.run))
      }
    } yield r
  }

  /****************************** indexing ******************************/

  /**
   * construct the actual url to send documents to based on the configuration
   * parameters, taking into account the index data pattern and prefix.
   */
  def indexURL: Reader[ElasticCfg, String] = Reader { es =>
    val date = new SimpleDateFormat(es.dateFormat).format(new Date)
    s"${es.url}/${es.indexName}-$date"
  }

  /**
   * given the ES url, make sure that all our requests are properly set with
   * the application/json content mime type.
   */
  def esURL: Reader[ElasticCfg, String] = Reader {
    es => s"${indexURL(es)}/${es.typeName}"
  }

  def esBulkURL: Reader[ElasticCfg, String] = Reader {
    es => s"${indexURL(es)}/${es.typeName}/_bulk"
  }

  private def esTemplateURL: Reader[ElasticCfg, String] = Reader {
    es => s"${es.url}/_template/${es.templateName}"
  }

  private def esTemplate: Reader[ElasticCfg, String] = Reader {
    cfg => cfg.templateLocation.map(
      f => scala.io.Source.fromFile(new File(f).getAbsolutePath).mkString
    ).getOrElse(
      sys.error("no index mapping template specified.")
    )
  }

  /**
   * returns true if the index was created. False if it already existed.
   */
  def ensureIndex(url: Reader[ElasticCfg, String])(H: HttpLayer): ES[Boolean] =
    ensureExists(
      HEAD(url),
      //create index
      PUT(url, Reader((cfg: ElasticCfg) => Json("settings" := Json("index.cache.query.enable" := true)).nospaces))
    )(H)

  /**
   * ensure the index we are trying to send documents too (defined in the config)
   * exists in the backend elastic search cluster.
   */
  def ensureExists(check: HttpOp, action: HttpOp)(H: HttpLayer): ES[Boolean] =
    for {
      s <- H.http(check).mapK(_.attempt)
      b <- s.fold(
             {
               case HttpException(404) => H.http(action).map(_ => true)
               case e => lift(Task.fail(e)) // SPLODE!
             },
             z => lift(Task.now(false))
             )
  } yield b

  /**
   * load the template specified in the configuration and send it to the index
   * to ensure that all the document fields we publish are correctly handled by
   * elastic search / kibana.
   */
  def ensureTemplate(H: HttpLayer): ES[Unit] = ensureExists(
    HEAD(
      esTemplateURL.map {s => log.info(s"Ensuring Elastic template $s exists..."); s }
    ),
    PUT(esTemplateURL, esTemplate)
  )(H).map(_ => ())
}