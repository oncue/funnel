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
package jmx

import cjmx.util.jmx.{Attach, MBeanQuery, RichMBeanServerConnection, JMXConnection, JMX}
import journal.Logger
import scalaz.stream.{Process,Channel,io,time}
import scalaz.concurrent.{Task,Strategy}
import scalaz.std.option._
import scalaz.syntax.std.option._
import javax.management.remote.JMXConnector
import javax.management.{MBeanServerConnection, ObjectName, Attribute, MBeanInfo}
import java.util.concurrent.ConcurrentHashMap
import scala.collection.JavaConverters._
import scala.util.control.NonFatal
import scala.concurrent.duration._
import InstrumentKinds._

case class JMXImportException(override val getMessage: String) extends RuntimeException

object Import {
  import JMXConnection._
  import Monitoring.{serverPool,schedulingPool}
  val S = Strategy.Executor(serverPool)

  private[this] val log = Logger[Import.type]

  type ConnectorCache = ConcurrentHashMap[String, JMXConnector]

  /**
   * Periodically fetch metrics from the specified JMX location.
   * See the configuration agent.cfg for details on specifiying queries
   * and attribute exclusion rules.
   */
  def periodically(
    location: String,
    queries: Seq[MBeanQuery],
    exclusions: String => Boolean,
    cluster: String
  )(cache: ConnectorCache, inst: Instruments
  )(frequency: Duration = 10.seconds
  ): Process[Task,Unit] =
    time.awakeEvery(frequency)(S, schedulingPool)
      .evalMap(_ => now(location, queries, exclusions, cluster)(cache, inst))

  /**
   * Fetch metrics from the specified JMX location and import them right now.
   * See the configuration agent.cfg for details on specifiying queries
   * and attribute exclusion rules.
   */
  def now(
    location: String,
    queries: Seq[MBeanQuery],
    exclusions: String => Boolean,
    cluster: String
  )(cache: ConnectorCache, inst: Instruments): Task[Unit] =
    for {
      a <- fetch(location, queries, exclusions)(cache)
      r  = InstrumentRequest(cluster, a:_*)
      _ <- RemoteInstruments.metricsFromRequest(r)(inst)
      _  = log.debug(s"Imported ${a.length} metrics from $location")
    } yield ()

  /**
   * Fetch metrics from the specified JMX location and return all the
   * metrics as an in-memory set.
   * See the configuration agent.cfg for details on specifiying queries
   * and attribute exclusion rules.
   */
  def fetch(
    location: String,
    queries: Seq[MBeanQuery],
    exclusions: String => Boolean
  )(cache: ConnectorCache
  ): Task[Seq[ArbitraryMetric]] = {
    log.debug("Retrieving metrics from the JMX endpoint...")
    remoteConnector(location)(cache).map(connector =>
      queries.flatMap(q => specific(q)(connector.mbeanServer))
      .filterNot { case (a,b) => exclusions(JMX.humanizeKey(b.getName)) }
      .flatMap { case (a,b) => toArbitraryMetric(a,b) }).handleWith {
        case NonFatal(e) =>
          log.error(s"An error occoured with the JMX import from $location")
          e.printStackTrace
          Task.now(Seq.empty[ArbitraryMetric])
      }
  }

  /**
   * Convert the JMX types to the primitive ArbitraryMetric so
   * that it can be injected into the RemoteInstruments API.
   */
  private[jmx] def toArbitraryMetric(obj: ObjectName, attribute: Attribute): Option[ArbitraryMetric] = {
    for {
      n <- Parser.parse(obj.getCanonicalName, JMX.humanizeKey(attribute.getName)).toOption
      v <- Option(attribute.getValue)
      k  = v.getClass.getSimpleName.toLowerCase
    } yield {
      val kind = k match {
        case "double" | "long" | "integer" => GaugeDouble
        case _                             => GaugeString
      }
      ArbitraryMetric(n, kind, Option(JMX.humanizeValue(v)))
    }
  }

  /**
   * We dont want to constantly thrash making connections to other VMs on the same host
   * so instead we will keep some state around about the connectors that are already avalible
   * and use those instead. It's basically a cache to avoid needless re-evaluation.
   */
  private[jmx] def remoteConnector(location: String)(cache: ConnectorCache): Task[JMXConnector] =
    if(cache.containsKey(location)) Task.now(cache.get(location))
    else {
      for {
        a <- Attach.remoteConnect(location, credentials = None).fold(e => Task.fail(JMXImportException(e)), Task.now)
        _ <- Task.delay(cache.putIfAbsent(location, a))
      } yield a
    }

  private[jmx] def specific(query: MBeanQuery)(svr: MBeanServerConnection): Set[(ObjectName, Attribute)] = {
    val getAttributeNames: ObjectName => Set[(ObjectName,Attribute)] = { on =>
      val info = svr.getMBeanInfo(on).getAttributes
      info.filter(_.isReadable)
          .flatMap(attr => svr.attribute(on, attr.getName)
          .map(on -> _)).toSet
    }
    svr.toScala.queryNames(query).flatMap(getAttributeNames)
  }

  private[jmx] def all(svr: MBeanServerConnection): Set[(ObjectName, Attribute)] =
    specific(MBeanQuery.All)(svr)

  private[jmx] def readableAttributeNames(svr: MBeanServerConnection, query: Option[MBeanQuery]): Set[String] = {
    val getAttributeNames: ObjectName => Set[String] = on =>
      svr.getMBeanInfo(on).getAttributes.filter(_.isReadable).map { _.getName }.toSet
    query.cata(q => safely(Set.empty[String]){
      svr.toScala.queryNames(q).flatMap(getAttributeNames) }, Set.empty[String])
  }

  private[this] def safely[A](onError: => A)(f: => A): A =
    try f catch { case NonFatal(e) => onError }

}
