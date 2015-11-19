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

import scala.concurrent.duration._

case class ElasticCfg(
  url: String,                         // Elastic URL
  indexName: String,                   // Name prefix of elastic index
  typeName: String,                    // Name of the metric type in ES
  dateFormat: String,                  // Date format for index suffixes
  templateName: String,                // Name of ES index template
  templateLocation: Option[String],    // Path to index template to use
  http: dispatch.Http,                 // HTTP driver
  groups: List[String],                // Subscription groups to publish to ES
  subscriptionTimeout: FiniteDuration, // Maximum interval for publishing to ES
  bufferSize: Int                      // Size of circular buffer in front of ES
)

import dispatch._, Defaults._

object ElasticCfg {
  def apply(
    url: String,
    indexName: String,
    typeName: String,
    dateFormat: String,
    templateName: String,
    templateLocation: Option[String],
    groups: List[String],
    subscriptionTimeout: FiniteDuration = 10.minutes,
    connectionTimeoutMs: Duration = 5000.milliseconds,
    bufferSize: Int = 4096
  ): ElasticCfg = {
    val driver: Http = Http.configure(
      _.setAllowPoolingConnection(true)
       .setConnectionTimeoutInMs(connectionTimeoutMs.toMillis.toInt))
    ElasticCfg(url, indexName, typeName, dateFormat,
               templateName, templateLocation, driver, groups, subscriptionTimeout, bufferSize)
  }
}
