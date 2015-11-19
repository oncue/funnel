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
package static

import java.net.URI

import dispatch.Http
import knobs._

import concurrent.duration.Duration
import scalaz._, Scalaz._
import scalaz.concurrent.{Strategy,Task}
import scalaz.stream.Process
import scalaz.stream.async.signalOf

case class StaticConfig(
  network: NetworkConfig,
  commandTimeout: Duration,
  targets: Map[TargetID, Set[Target]],
  flasks: Map[FlaskID, Flask],
  state: StateCache
) extends PlatformConfig {
  val discovery: Discovery = new StaticDiscovery(targets, flasks)
  val sharder = RandomSharding
  val http: Http = Http.configure(
    _.setAllowPoolingConnection(true)
     .setConnectionTimeoutInMs(commandTimeout.toMillis.toInt))
  val signal = signalOf(true)(Strategy.Executor(Chemist.serverPool))
  val remoteFlask = new HttpFlask(http)
  val templates = List.empty
  val maxInvestigatingRetries = 6
}

object Config {
  def readConfig(cfg: MutableConfig): Task[StaticConfig] = for {
    network     <- readNetwork(cfg.subconfig("chemist.network"))
    timeout     <- cfg.require[Duration]("chemist.command-timeout")
    cachetype   <- cfg.lookup[String]("chemist.state-cache")
    subi        <- cfg.base.at("chemist.instances")
    subf        <- cfg.base.at("chemist.flasks")
    instances    = readInstances(subi)
    flasks       = readFlasks(subf)
    statecache   = readStateCache(cachetype)
  } yield StaticConfig(network, timeout, instances, flasks, statecache)

  private def readNetwork(cfg: MutableConfig): Task[NetworkConfig] = for {
    host   <- cfg.require[String]("host")
    port   <- cfg.require[Int]("port")
  } yield NetworkConfig(host, port)

  private def readStateCache(c: Option[String]): StateCache =
    c match {
      case Some("memory") => MemoryStateCache
      case _              => MemoryStateCache
    }

  private def readLocation(cfg: Config): Location =
    Location(
      host             = cfg.require[String]("host"),
      port             = cfg.require[Int]("port"),
      datacenter       = cfg.require[String]("datacenter"),
      protocol         = cfg.lookup[String]("protocol"
        ).flatMap(NetworkScheme.fromString
        ).getOrElse(NetworkScheme.Http),
      intent = LocationIntent.fromString(
        cfg.require[String]("intent")
        ).getOrElse(LocationIntent.Mirroring),
      templates        = cfg.require[List[String]]("target-resource-templates").map(LocationTemplate)
    )

  private def readFlasks(cfg: Config): Map[FlaskID, Flask] = {
    val ids: Vector[String] = cfg.env.keys.map(_.toString.split('.')(0)).toVector
    ids.toVector.map { id =>
      val loc = readLocation(cfg.subconfig(s"$id.location"))
      FlaskID(id) -> Flask(FlaskID(id), loc)
    }.toMap
  }

  private def readInstances(cfg: Config): Map[TargetID, Set[Target]]= {
    val ids: Vector[String] = cfg.env.keys.map(_.toString.split('.')(0)).toVector
    ids.toVector.map { id =>
      val sub = cfg.subconfig(id)
      val cn = sub.require[String]("clusterName")
      val uris = sub.require[List[String]]("uris")
      TargetID(id) -> uris.map(u => Target(cn, new URI(u))).toSet
    }.toMap
  }
}
