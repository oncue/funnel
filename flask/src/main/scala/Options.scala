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
package flask

import funnel.elastic.ElasticCfg
import scala.concurrent.duration._

case class Options(
  name: Option[String],
  cluster: Option[String],
  retriesDuration: Duration,
  maxRetries: Int,
  elasticExploded: Option[ElasticCfg] = None,
  elasticFlattened: Option[ElasticCfg] = None,
  collectLocalMetrics: Option[Boolean] = None,
  localMetricFrequency: Option[Int] = None,
  funnelPort: Int = 5775,
  selfiePort: Int = 7557,
  metricTTL: Option[Duration] = None,
  environment: String
)

import knobs.Config
import journal.Logger
import scalaz.std.option._
import scalaz.syntax.applicative._

object Options {
  val log = Logger[Options.type]

  def readConfig(cfg: Config): Options = {
    log.debug(s"input configuration was: $cfg")
    val name             = cfg.lookup[String]("flask.name")
    val cluster          = cfg.lookup[String]("flask.cluster")
    val environment      = cfg.lookup[String]("flask.environment").getOrElse("unknown")
    val retriesDuration  = cfg.require[Duration]("flask.retry-schedule.duration")
    val maxRetries       = cfg.require[Int]("flask.retry-schedule.retries")

    val httpPort         = cfg.lookup[Int]("flask.network.http-port").getOrElse(5775)
    val selfiePort       = cfg.lookup[Int]("flask.network.selfie-port").getOrElse(7557)
    val metricTTL        = cfg.lookup[Duration]("flask.metric-ttl")
    val collectLocal     = cfg.lookup[Boolean]("flask.collect-local-metrics")
    val localFrequency   = cfg.lookup[Int]("flask.local-metric-frequency")

    Options(
      name                 = name,
      cluster              = cluster,
      retriesDuration      = retriesDuration,
      maxRetries           = maxRetries,
      elasticExploded      = readElastic(cfg.subconfig("flask.elastic-search-exploded")),
      elasticFlattened     = readElastic(cfg.subconfig("flask.elastic-search-flattened")),
      collectLocalMetrics  = collectLocal,
      localMetricFrequency = localFrequency,
      funnelPort           = httpPort,
      selfiePort           = selfiePort,
      metricTTL            = metricTTL,
      environment          = environment
    )
  }

  private def readElastic(cfg: Config): Option[ElasticCfg] = {
    val elasticURL       = cfg.lookup[String]("url")
    val elasticIx        = cfg.lookup[String]("index-name")
    val elasticTy        = cfg.lookup[String]("type-name")
    val elasticDf        = cfg.lookup[String]("partition-date-format").getOrElse("yyyy.MM.ww")
    val elasticTimeout   = cfg.lookup[Duration]("connection-timeout").getOrElse(5.seconds)
    val esGroups         = cfg.lookup[List[String]]("groups")
    val esTemplate       = cfg.lookup[String]("template.name").getOrElse("flask")
    val esTemplateLoc    = cfg.lookup[String]("template.location")
    val esPublishTimeout = cfg.lookup[Duration]("minimum-publish-frequency").getOrElse(10.minutes)
    (elasticURL |@| elasticIx |@| elasticTy |@| esGroups)(
      ElasticCfg(_, _, _, elasticDf, esTemplate, esTemplateLoc, _, esPublishTimeout.toNanos.nanos, elasticTimeout))
  }
}