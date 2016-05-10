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

import java.net.URI
import scala.collection.JavaConverters._
import scalaz.concurrent.Task
import scalaz.{\/,NonEmptyList,Nondeterminism}
import scalaz.std.vector._
import scalaz.syntax.monadPlus._
import scalaz.syntax.std.option._
import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.{Instance => AWSInstance}
import journal.Logger
import funnel.aws._

/**
 * This module contains functions for describing the deployed world as the caller
 * sees it. Functions are provided to look up instance metadata for a single host,
 * multiple hosts, or listing the entire known world.
 *
 * The assumptions that have been made about hosts are:
 * 1. They are network accessible from the *Chemist* perspective
 * 2. They are running a Funnel server on port 5775 - this is used to validate that
 *    the instance is in fact a useful server for the sake of work sharding.
 */
class AwsDiscovery(
  ec2: AmazonEC2,
  asg: AmazonAutoScaling,
  classifier: Classifier[AwsInstance],
  resourceTemplates: Seq[LocationTemplate],
  cacheMaxSize: Int = 2000
) extends Discovery {
  import Chemist.contact
  import metrics.discovery._

  // Hack to make instruments init themselves up front
  val _ = metrics.discovery.ValidateLatency

  type AwsInstanceId = String

  private val log = Logger[AwsDiscovery]

  private val cache = Cache[AwsInstanceId, AwsInstance](
    maximumSize = Some(cacheMaxSize))

  private val allTemplates: Map[NetworkScheme, Seq[LocationTemplate]] =
    NetworkScheme.all.foldLeft(Map.empty[NetworkScheme,Seq[LocationTemplate]]){ (a,b) =>
      a + (b -> resourceTemplates.filter(_.has(b)))
    }

  ///////////////////////////// public api /////////////////////////////

  /**
    * Lists all of the instances in the given AWS account and groups them into
    * funnel targets and flasks.
    *
    * Instance is considered to be monitorable if it responds to a rudimentary
    * verification that Funnel is running on port 5775 and is network accessible.
    * If network accessibility check fails then instance is considered unmonitorable.
    *
    *
    */
  def inventory: Task[DiscoveryInventory] = ListInventory.timeTask {
    for {
      a <- readAutoScalingGroups
      b <- classifier.task

      _ = metrics.model.hostsTotal.set(a.size)
      m = a.filter((b andThen isMonitorable)(_))
      _ = metrics.model.hostsValid.set(m.size)

      v <- valid(a)
      um = a.toSet &~ v.toSet
      _ = metrics.model.hostsInValid.set(um.size)

      af = a.filter((b andThen isActiveFlask)(_))
      _ = metrics.model.hostsActiveFlask.set(af.size)

      f = a.filter((b andThen isFlask)(_))
      _ = metrics.model.hostsFlask.set(f.size)

      _  = log.info(s"[inventory] instances=${a.size}, monitorable=${m.size}, unmonitorable=${um.size} activeFlasks=${af.size} flasks=${af.size}")

    } yield DiscoveryInventory(
      m.map(in => TargetID(in.id) -> in.targets),
      um.toSeq.map(in => TargetID(in.id) -> in.targets),
      af.map(i => Flask(FlaskID(i.id), i.location.copy(port = 5775))), //FIXME: we really need to be reading control port from EC2 tags!
      f.map(i => Flask(FlaskID(i.id), i.location.copy(port = 5775)))
    )
  }

  /**
   * Lookup the `AwsInstance` for a given `InstanceID`; `AwsInstance` returned contains all
   * of the useful AWS metadata encoded into an internal representation.
   */
  def lookupFlask(id: FlaskID): Task[Flask] =
    lookupOne(id.value).map(a => Flask(FlaskID(a.id), a.location))

  /**
   * Lookup the `AwsInstance` for a given `InstanceID`; `AwsInstance` returned contains all
   * of the useful AWS metadata encoded into an internal representation.
   */
  def lookupTargets(id: TargetID): Task[Set[Target]] =
    lookupMany(Seq(id.value)).flatMap {
      _.find(_.id == id.value) match {
        case None    => Task.fail(InstanceNotFoundException(id.value))
        case Some(i) => Task.now(i.targets)
      }
    }

  /**
   * Lookup the `AwsInstance` for a given `InstanceID`; `AwsInstance` returned contains all
   * of the useful AWS metadata encoded into an internal representation.
   */
  def lookupOne(id: String): Task[AwsInstance] = {
    lookupMany(Seq(id)).flatMap {
      _.find(_.id == id) match {
        case None => Task.fail(InstanceNotFoundException(id))
        case Some(i) => Task.now(i)
      }
    }
  }

  /**
   * Lookup the `AwsInstance` metadata for a set of `InstanceID`.
   * @see funnel.chemist.AwsDiscovery.lookupOne
   */
  protected def lookupMany(ids: Seq[String]): Task[Seq[AwsInstance]] = {
    def lookInAws(specificIds: Seq[String]): Task[Seq[AwsInstance]] =
      if (specificIds.isEmpty)
        Task.now(Seq.empty)
      else
        LookupManyAws.timeTask {
          for {
            a <- EC2.reservations(specificIds)(ec2)
            b <- Task.now(a.flatMap(_.getInstances.asScala.map(fromAWSInstance)))
            (ignored,eligible) = b.toVector.separate
            _  = log.info(s"[lookupMany] reservations=${a.length} eligible=${eligible.length} ignored=${ignored.length}")
            _  = ignored.groupBy(_._2).mapValues(_.map(_._1)).foreach {
              case (reason, lst) => log.warn(s"[lookupMany] ignored ${lst.size} instances, reason='$reason', ids=$lst")
            }
          } yield eligible
        }.handleWith {
          case t =>
            LookupAwsFailure.increment
            Task.fail(t)
        }

    val (missing, found) = cache lookup ids

    if (missing.nonEmpty)
      log.info(s"[lookupMany] missing=${missing.length}, cached=${found.length}")

    lookInAws(missing).flatMap(is =>
      Task.delay {
        cache.putAll(is.map(i => (i.id, i)))
        is
      }).map(_ ++ found)
  }

  ///////////////////////////// internal api /////////////////////////////

  /**
   * Goal of this function is to validate that the machine instances specified
   * by the supplied group `g`, are in fact running a funnel instance and it is
   * ready to start sending metrics if we connect to its `/stream` function.
   */
  private def validate(instance: AwsInstance): Task[AwsInstance] =
    ValidateLatency.timeTask {
      /**
       * Do a naive check to see if the socket is even network accessible.
       * Given that funnel is using multiple protocols we can't assume that
       * any given protocol at this point in time, so we just try to see if
       * its even available on the port the discovery flow said it was.
       *
       * This mainly guards against miss-configuration of the network setup,
       * LAN-ACLs, firewalls etc.
       */
      for {
        a <- Task(contact(instance.location.uri))(Chemist.serverPool)
        b <- a.fold(
          e => {
            log.info(s"failed validation instance=${instance.id} uri=${instance.location.uri} reason=$e")
            Task.fail(e)
          },
          o => Task.now(o)
        )
      } yield instance
    }

  /**
   * This is the main work-horse function of discovery and provides a mechanism
   * to find all the instances that are contained within an auto-scaling group.
   */
  private def instances(g: Classification => Boolean): Task[Seq[AwsInstance]] =
    for {
      a <- readAutoScalingGroups
      b <- classifier.task
      c  = b andThen g
      // apply the specified filter if we want to remove specific groups for a reason
      d  = a.filter(c(_))
      // _  = println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>")
      // _  = a.filter(c(_)).foreach { i => println(s"i: ${i.application.map(_.name)} -> ${b(i)} -> ${c(i)}") }
      // _  = println("<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<")
      _  = log.debug(s"instances, discovered=${d.size}, filtered=${a.size - d.size}")
    } yield d

  /**
   * A monadic function that asynchronously filters the passed instances for validity.
   */
  private def valid(instances: Seq[AwsInstance]): Task[Seq[AwsInstance]] = for {
    x <- Task.now(instances)
    // actually reach out to all the discovered hosts and check that their port is reachable
    y = x.map(g => validate(g).attempt)
    // y = x.map(g => Task.now(g).attempt) // FOR LOCAL TESTING
    // run the tasks on the specified thread pool (Server.defaultPool)
    b <- Nondeterminism[Task].gatherUnordered(y)
    r = b.flatMap(_.toList)
    _ = log.debug(s"[validateInstances], instances=${r.map(_.id).mkString(", ")}")
  } yield r

  /**
   * This is kind of horrible, but it is what it is. The AWS api's really do not help here at all.
   * Sorry!
   */
  private def readAutoScalingGroups: Task[Seq[AwsInstance]] =
    for {
      g <- ASG.list(asg)
      _  = log.info(s"[readAutoScalingGroups] found asgs=${g.length} instances=${g.map(_.instances.length).sum}")
      r <- lookupMany(g.flatMap(_.instances.map(_.getInstanceId)))
    } yield r

  private[aws] def toLocation(
    dns: String,
    datacenter: String,
    template: Option[String],
    intent: LocationIntent): String \/ Location =
    for {
      a <- Option(dns).filter(_.nonEmpty
        ) \/> s"instance had no address: $dns"

      b <- template \/> s"instance does not have tag with funnel template uri"

      c  = new URI(LocationTemplate(b).build("@host" -> a))

      d <- Location.fromURI(c, datacenter, intent, allTemplates
        ) \/> s"unable to create a location from uri '$c'"
    } yield d

  /**
   * The EC2 instance in question should have the following tags:
   *
   * 1. `funnel:mirror:uri-template` - should be a qualified uri that denotes the
   * host that chemist should be able to find the mirroring endpoint. Example would
   * be `http://@host:5775`. Supported URI schemes are `http` and `tcp` (where the
   * latter is a zeromq PUB socket).
   *
   * 2. `funnel:telemetry:uri-template` - should be a qualified uri that denotes the
   * host that chemist should be able to find the admin telemetry socket. Only valid
   * protocol is `tcp` for a zeromq PUB socket.
   *
   * In case of failure to convert we return (InstanceId, Message)
   */
  private[aws] def fromAWSInstance(in: AWSInstance): (String, String) \/ AwsInstance = {
    import LocationIntent._

    val machineTags = in.getTags.asScala.map(t => t.getKey -> t.getValue).toMap

    val dns = in.getPrivateDnsName

    val datacenter = in.getPlacement.getAvailabilityZone

    //Note: if node is not labeled explicitly we ignore it to avoid wasting time trying to continiously
    // reconnect to incompatible instances
    val mirrorTemplate: Option[String] = machineTags.get(AwsTagKeys.mirrorTemplate)

    val r = for {
      a <- toLocation(dns, datacenter, mirrorTemplate, Mirroring)
      // _  = log.debug(s"discovered mirroring template '$a'")
    } yield AwsInstance(
      id = in.getInstanceId,
      tags = machineTags,
      locations = NonEmptyList(a)
    )

    r.leftMap(message => (in.getInstanceId, message))
  }
}
