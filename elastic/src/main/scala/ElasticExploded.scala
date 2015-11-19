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

import java.net.URI
import scalaz.Kleisli
import java.util.Date
import java.text.SimpleDateFormat
import scalaz.concurrent.Strategy.Executor
import scalaz.stream.{Process1,Process,time,async,wye}

/**
 * This class provides a consumer for the funnel montioring stream that takes
 * the events emitted and constructs a JSON document that will be sent to elastic
 * search. The names of the metrics on the stream are expected to be delimited
 * by a `/`, which will then be exploded into a tree of fields.
 *
 * WARNING: Only use this sink if you have a small-medium size system as elastic-
 * search performs where there is low document-cardinality. This format is convenient
 * for certain types of use case, but it is not expected that this works at large scale.
 *
 * The resulting document structure looks like:
 *
 * {
 *   "cluster": "imqa-maestro-1-0-279-F6Euts",  #This allows for a Kibana search, cluster: x
 *   "host": "ec2-107-22-118-178.compute-1.amazonaws.com",
 *   "jvm": {
 *     "memory": {
 *       "heap": {
 *         "committed": {
 *           "last": 250.99763712000001,
 *           "mean": 250.99763712000001,
 *           "standard_deviation": 0.0
 *         },
 *         "usage": {
 *           "last": 0.042628084023299997,
 *           "mean": 0.042445506024100001,
 *           "standard_deviation": 0.00018257799924300001
 *         }
 *       }
 *     }
 *   }
 * }
 *
 */
case class ElasticExploded(M: Monitoring){

  import Process._
  import Elastic._
  import http.JSON._
  import argonaut._, Argonaut._
  import scala.concurrent.duration._

  /**
   * Data points grouped by mirror URL, experiment ID, experiment group,
   * and grouping key from config
   */
  type ESGroup[A] = Map[GroupKey, Map[Path, Datapoint[A]]]

  case class GroupKey(
    /* name of the group, e.g. now */
    name: String,
    /* source of this group, specifically a uri */
    source: Option[SourceURL],
    /* if applicable, an experiment id */
    experimentID: Option[ExperimentID],
    /* if applicable, an experiment id */
    experimentGroup: Option[GroupID]
  )

  /**
   * Groups data points by key, mirror URL, and custom grouping from config.
   * Emits when it receives a key/mirror where the key is already in the group for the mirror.
   * That is, emits as few times as possible without duplicates
   * and without dropping any data.
   */
  def elasticGroup[A](groups: List[String]): Process1[Option[Datapoint[A]], ESGroup[A]] = {
    def go(sawDatapoint: Boolean, m: ESGroup[A]): Process1[Option[Datapoint[A]], ESGroup[A]] =
      await1[Option[Datapoint[A]]].flatMap {
        case Some(pt) =>
          val name = pt.key.name
          val source = pt.key.attributes.get(AttributeKeys.source)
          val experimentID = pt.key.attributes.get(AttributeKeys.experimentID)
          val experimentGroup = pt.key.attributes.get(AttributeKeys.experimentGroup)
          val grouping = groups.find(name startsWith _) getOrElse ""
          val k = name.drop(grouping.length).split("/").toList.filterNot(_ == "")
          val groupKey = GroupKey(grouping, source, experimentID, experimentGroup)
          m.get(groupKey) match {
            case Some(g) => g.get(k) match {
              case Some(_) =>
                emit(m) ++ go(true, Map(groupKey -> Map(k -> pt)))
              case None =>
                go(true, m + (groupKey -> (g + (k -> pt))))
            }
            case None =>
              go(true, m + (groupKey -> Map(k -> pt)))
          }
        case None =>                    // No Datapoint this time
          if (sawDatapoint) {           // Saw one last time
            go(false, m)                // Keep going with current Map
          } else {                      // Didn't see one last time, either
            M.log.info("I haven't seen any data points for a while. I hope everything's alright.")
            emit(m) ++ go(false, Map()) // Publish current Map
          }
      }
    go(false, Map())
  }

  /**
   * Emits one JSON document per mirror URL and window type, on the right,
   * first emitting the ES mapping properties for their keys, on the left.
   * Once grouped by `elasticGroup`, this process emits one document per
   * URL/window with all the key/value pairs that were seen for that mirror
   * in the group for that period.
   *
   * For the fixed fields `uri` and `host`, if we do not have a meaningful
   * value for this, we fallback to assuming this is coming from the local
   * monitoring instance, so just use the supplied flask name.
   */
  def elasticUngroup[A](flaskName: String, flaskCluster: String): Process1[ESGroup[A], Json] =
    await1[ESGroup[A]].flatMap { g =>
      M.log.debug(s"Publishing ${g.size} elements to ElasticSearch")
      emitAll(g.toSeq.map { case (groupKey, m) =>
        ("uri" := groupKey.source.getOrElse(flaskName)) ->:
        ("host" := groupKey.source.map(u => (new URI(u)).getHost).getOrElse(flaskName)) ->:
        ("experiment_id" :=? groupKey.experimentID ) ->?:
        ("experiment_group" :=? groupKey.experimentGroup ) ->?:
        ("@timestamp" :=
          new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZZ").format(new Date)) ->:
          m.toList.foldLeft(("group" := groupKey.name) ->: jEmptyObject) {
            case (o, (ps, dp)) =>
              val attrs = dp.key.attributes
              val kind = attrs.get(AttributeKeys.kind)
              val clust = ("cluster" :=
                attrs.get(AttributeKeys.cluster).getOrElse(flaskCluster)) ->: jEmptyObject
              clust deepmerge (o deepmerge (ps ++ kind).foldRight((dp.asJson -| "value").get)(
                (a, b) => (a := b) ->: jEmptyObject))
          }
      })
    }.repeat

  /**
   *
   */
  def publish(flaskName: String, flaskCluster: String): ES[Unit] = {
    val E = Executor(Monitoring.defaultPool)
    bufferAndPublish(flaskName, flaskCluster)(M, E){ cfg =>
      val subscription = Monitoring.subscribe(M){ k =>
        cfg.groups.exists(g => k.startsWith(g))}.map(Option.apply)

      time.awakeEvery(cfg.subscriptionTimeout)(E,
        Monitoring.schedulingPool).map(_ => Option.empty[Datapoint[Any]]
      ).wye(subscription)(wye.merge)(E) |>
      elasticGroup(cfg.groups) |>
      elasticUngroup(flaskName, flaskCluster)
    }
  }
}