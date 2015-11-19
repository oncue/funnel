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
package integration

import concurrent.duration._
import chemist.{Flask,FlaskID,Location,LocationIntent,LocationTemplate,Target,NetworkScheme}
import flask.Options
import java.net.URI
import zeromq.TCP

object IntegrationFixtures {
  val defaultTemplates =
    List(LocationTemplate("http://@host:@port/stream/previous"))

  val localhost: Location =
    Location(
      host = "127.0.0.1",
      port = 5775,
      datacenter = "local",
      protocol = NetworkScheme.Http,
      intent = LocationIntent.Mirroring,
      templates = defaultTemplates)

  val flask1 = Flask(FlaskID("flask1"), localhost)

  val flask1Options = Options(
    name = Some(flask1.id.value),
    cluster = None,
    retriesDuration = 2.seconds,
    maxRetries = 0,
    funnelPort = flask1.location.port,
    selfiePort = 7557,
    environment = "test")

  val flask2 = Flask(FlaskID("flask2"), localhost.copy(port = 5776))

  val flask2Options = Options(
    name = Some(flask2.id.value),
    cluster = None,
    retriesDuration = 2.seconds,
    maxRetries = 0,
    funnelPort = flask2.location.port,
    selfiePort = 7558,
    environment = "test")

  val flaskOptionsWithES = flask1Options.copy(
    elasticExploded = Some(elastic.ElasticCfg(
      url = "http://localhost:9200",
      indexName = "funnel",
      typeName = "metric",
      dateFormat = "yyyy.MM.ww",
      templateName = "flask",
      templateLocation = None,
      groups = List("previous/jvm", "previous/system", "previous")
    )))

  lazy val flasks =
    flask1 ::
    flask2 :: Nil

  lazy val targets =
    target01 ::
    target02 ::
    target03 :: Nil

  val target01 = Target(
    cluster = "target01",
    uri = new URI("http://localhost:4001/stream/now")
  )
  val target02 = Target(
    cluster = "target02",
    uri = new URI("http://localhost:4002/stream/now")
  )
  val target03 = Target(
    cluster = "target03",
    uri = new URI("http://localhost:4003/stream/now")
  )
}