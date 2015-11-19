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
package mesos

import funnel.agent.InstrumentKinds._
import funnel.agent.InstrumentRequest
import journal.Logger

import scala.util.matching.Regex
import scalaz._
import java.net.URI
import scala.io.Source
import scalaz.stream.{ Process, time }
import scalaz.concurrent.{Task,Strategy}
import scala.concurrent.duration._
import scala.util.control.NonFatal
import argonaut._, Argonaut._


case class MetricsChecker(field:  Int)


object Import {
  import Monitoring.{serverPool,schedulingPool}
  val S = Strategy.Executor(serverPool)

  private[this] val log = Logger[Import.type]

  /**
   * Periodically fetch metrics from the specified url location.
   * See the configuration agent.cfg for details on specifiying queries
   */
  def periodically(
                    url: URI,
                    queries: List[String],
                    checkfield: String,
                    cluster: String
                    )(inst: Instruments
                    )(frequency: Duration = 10.seconds
                    ): Process[Task,Unit] =
    time.awakeEvery(frequency)(S, schedulingPool)
      .evalMap(_ => now(url, queries, checkfield, cluster)(inst))


  /**
   * Fetch metrics from the specified url location and import them right now.
   * See the configuration agent.cfg for details on specifiying queries
   */
  def now(
           url: URI,
           queries: List[String],
           checkfield: String,
           cluster: String
           )(inst: Instruments): Task[Unit] =  {
    for {
      a <- fetch(url, queries, checkfield)
      r = InstrumentRequest(cluster, a: _*)
      _ <- RemoteInstruments.metricsFromRequest(r)(inst)
      _ = log.debug(s"Imported ${url} metrics from $url")
    } yield ()
  }

  private[this] def fetch(url: URI): Task[String] =
    Task.now(Source.fromInputStream(url.toURL.openConnection.getInputStream).mkString)


  def fetch(url: URI, queries: List[String], checkfield: String): Task[Seq[ArbitraryMetric]] =  {
   fetch(url).map(jsonString => {fetch(jsonString, queries, checkfield)}.get).handleWith {
     case NonFatal(e) =>
       log.error(s"An error occoured with the mesos import from $url")
       e.printStackTrace
       Task.now(Seq.empty[ArbitraryMetric])
   }
  }

  def fetch(jsonString: String, queries: List[String], checkfield: String): Option[Seq[ArbitraryMetric]] =  {
      val json = Parse.parse(jsonString)
      for {
        j <- json.toOption
        o <- j.obj if (checkpasses(j, checkfield))
      } yield (metricsQuery(queries).map {
        case (key, kind) => o.apply(key).map(v => toArbitraryMetric(key, v.toString, kind))
      }.flatten.toSeq
        )
    }
  /**
   * check if field exists and it's set to 1
   */
  private[mesos] def checkpasses(json: Json, checkfield: String): Boolean = {

    implicit def metricsDecodeJson: DecodeJson[MetricsChecker] =
      DecodeJson(c => for {
        field <- (c --\ checkfield).as[Int]
      } yield MetricsChecker(field))

    val jo = json.jdecode(metricsDecodeJson)

    jo.value match {
      case Some(MetricsChecker(1)) => true
      case _ => false
    }
  }

  /**
   * Convert the json to the primitive ArbitraryMetric so
   * that it can be injected into the RemoteInstruments API.
   */
  private[mesos] def toArbitraryMetric(name: String, value: String, kind: String): ArbitraryMetric = {
      val k = kind.toLowerCase() match {
        case "double" | "long" | "integer" => GaugeDouble
        case "counter" => Counter
        case "timer" => Timer
        case _                             => GaugeString
      }
      ArbitraryMetric(name, k, Option(value))
  }

  /**
   * Convert the query list in config to readable map
   * this is needed since we don't know which field is mapped to what in json and config needs to specify that
   * format of the config is "mesos_active_framework|counter"
   *
   */
  private[mesos] def metricsQuery(queries: List[String]) :Map[String,String] = {
    val matcher = new Regex("""([^:]+)?\|(.*)?""")
    queries.map({ case matcher(name,kind) => name -> kind }).toMap
  }
}
