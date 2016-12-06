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
package aws

import scalaz.NonEmptyList

/**
 * Represents an EC2 machine in AWS. Every single machine in AWS should
 * be monitorable, or at least have a monitorable location, even if it
 * is not ultimately reachable.
 *
 * Tags are used heavily to datamine what kind of instance we're looking
 * at, during various parts of the process. Your instances need the following
 * tags set on the instance (as a minimum):
 *
 * - `funnel:target:name`: e.g. myapp
 * - `funnel:target:version`: e.g. 1.2.3
 * - `funnel:target:qualifier`: e.g. XdfGeq4 (uniquely identify this deployment)
 * - `funnel:mirror:uri-template`: e.g. http://@host:5775; lets chemist
 *                                 know where to connect to for mirroring
 *
 * Do be aware that EC2 has a 10 tag limit (so dumb!)
  *
  * TODO: Future idea:
  *
  *  Migrate to a single 'funnel' tag with URI value (saves 3 EC2 tags, can add parameters easily) and encode
  *  additional info including:
  *    - role in the funnel ecosystem (e.g. chemist, flask, metric producer agent)
  *    - port where control APIs are exposed (may be different from metric port)
  *    - telemetry port for zeromq
  *    - (optional) scope. chemist/flasks/agents will avoid collecting metrics from
  *      a different scope. This will allow us to deploy multiple independent sets of chemist/flasks
  *      (for testing or to control different groups of resources. E.g. we can have
  *       dedicated chemist/flask deployment to monitor most critical or heaviest or backend agent.
  *       Or we can do test deployments in the same environment.)
  *
  * URI format for new tag:
  *   role://app-name/version/qualifier?mirror=port&telemetry=port&format=jsonOrZeromq&control=port
  * Examples:
  *   chemist://chemist/5.2.123/XdfGeq4?metricsPort=port&metricsFormat=json&controlPort=port&scope=default
  *   flask://flask/5.2.123/XdfGeq4?metricsPort=port&metricsProtocol=http&controlPort=port&scope=test
  *   target://s2s-admin/1.2.3/sadasfas?metricsPort=port&metricsProtocol=http&telemetry=port
 */
case class AwsInstance(
  id: String,
  tags: Map[String,String] = Map.empty,
  locations: NonEmptyList[Location]
){
  def location: Location =
    locations.head

  def application: Option[Application] = {
    for {
      b <- tags.get(AwsTagKeys.name) orElse tags.get("type") orElse tags.get("Name")
      c <- tags.get(AwsTagKeys.version) orElse tags.get("revision") orElse Some("unknown")
      d  = tags.get("aws:cloudformation:stack-name")
            .flatMap(_.split('-').lastOption.find(_.length > 3))
    } yield Application(
      name = b,
      version = c,
      qualifier = tags.get(AwsTagKeys.qualifier) orElse d
    )
  }

  def targets: Set[Target] =
    for {
      a <- application.toSet[Application]
      b <- findLocation(_.intent == LocationIntent.Mirroring).toSet[Location]
      c <- b.templatedPathURIs
    } yield Target(a.toString, c)

  private def findLocation(f: Location => Boolean): Seq[Location] =
    locations.list.filter(f).toList

}
