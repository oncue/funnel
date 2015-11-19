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
package http

import argonaut.{DecodeResult => D, _}
import argonaut._, Argonaut._

import java.io.InputStream
import java.util.concurrent.{ExecutorService, TimeUnit}
import scala.concurrent.duration._
import scalaz.concurrent.{Strategy,Task}
import scalaz.stream._

/**
  * Use this JSON construction to instruct the admin server of new URLs
  * that it should add to the incoming "mirror" stream.
  *
  * ````
  * [
  *   {
  *     "cluster": "accounts",
  *     "uris": [
  *       "http://sdfsd.com/sdf",
  *       "http://improd.dfs/sdfsd"
  *     ]
  *   }
  * ]
  * ````
  **/
case class Cluster(label: String, urls: List[String])

/** JSON encoders and decoders for types in the this library. */
object JSON {

  val R = Reportable
  val kindF = "kind"

  def encodeResult[A](a: A)(implicit A: EncodeJson[A]): Json = A(a)
  def encode[A](a: A)(implicit A: EncodeJson[A]): String = A(a).nospaces
  def prettyEncode[A](a: A)(implicit A: EncodeJson[A]): String = A(a).spaces2

  implicit val ClusterCodecJson: CodecJson[Cluster] =
    casecodec2(Cluster.apply, Cluster.unapply)("cluster", "uris")

  implicit val DoubleEncodeJson =
    jencode1[Double,Option[Double]] {
      case d if d.isNaN || d.isInfinity => None
      case d => Some(d)
    } (argonaut.EncodeJson.OptionEncodeJson(argonaut.EncodeJson.DoubleEncodeJson))

  def decodeUnion[A](kindF: String)(cases: (String, DecodeJson[A])*): DecodeJson[A] = {
    val byKind = cases.toMap
    DecodeJson { c =>
      (c --\ kindF).as[String].flatMap { kind =>
        byKind.get(kind).map(ctor => ctor(c))
              .getOrElse(D.fail("unknown kind: " + kind, c.history))
      }
    }
  }

  // "HELLO" -> "Hello", "hello" -> "hello", "hELLO" -> "hello"
  def unCapsLock(s: String): String =
    if (s.isEmpty) s
    else s(0) + s.drop(1).toLowerCase

  implicit def EncodeUnits: EncodeJson[Units] = {
    import Units._; import Units.Base._
    jencode1[Units, String] {
      case Bytes(Zero)  => "Bytes"
      case Bytes(Kilo)  => "Kilobytes"
      case Bytes(Mega)  => "Megabytes"
      case Bytes(Giga)  => "Gigabytes"
      case Duration(g)  => unCapsLock(g.toString)
      case Count        => "Count"
      case Ratio        => "Ratio"
      case TrafficLight => "TrafficLight"
      case Healthy      => "Healthy"
      case Load         => "Load"
      case None         => "None"
    }
  }

  implicit def DecodeUnits: DecodeJson[Units] = DecodeJson { c =>
    import Units._; import Units.Base._
    c.as[String] flatMap {
      case "Bytes" => D.ok { Bytes(Zero) }
      case "Kilobytes" => D.ok { Bytes(Kilo) }
      case "Megabytes" => D.ok { Bytes(Mega) }
      case "Gigabytes" => D.ok { Bytes(Giga) }
      case "Count" => D.ok { Count }
      case "Ratio" => D.ok { Ratio }
      case "TrafficLight" => D.ok { TrafficLight }
      case "Healthy" => D.ok { Healthy }
      case "Load" => D.ok { Load }
      case "None" => D.ok { None }
      case timeunit =>
        try D.ok(Duration(TimeUnit.valueOf(timeunit.toUpperCase)))
        catch { case e: IllegalArgumentException =>
          D.fail("invalid units: " + timeunit, c.history)
        }
    }
  }

  implicit def EncodeKey[A]: EncodeJson[Key[A]] = new EncodeJson[Key[A]] {
    val e = jencode4L((k: Key[A]) => (k.name, k.typeOf, k.units, k.description))(
      "name", "type", "units", "description")
    def encode(k: Key[A]) = e.encode(k).deepmerge(k.attributes.asJson)
  }

  implicit def DecodeKey: DecodeJson[Key[Any]] = DecodeJson { c => for {
    name   <- (c --\ "name").as[String]
    typeOf <- (c --\ "type").as[Reportable[Any]](DecodeReportableT)
    u      <- (c --\ "units").as[Units]
    desc   <- (c --\ "description").as[String].option
    attrs  <- c.as[Map[String, String]].map(_ - "name" - "type" - "units" - "description")
  } yield Key(name, typeOf, u, desc getOrElse "", attrs) }

  implicit def EncodeStats: EncodeJson[funnel.Stats] =
    jencode7L((s: funnel.Stats) =>
      (s.last, s.mean, s.count.toDouble, s.variance, s.standardDeviation, s.skewness, s.kurtosis))(
       "last", "mean", "count", "variance", "standardDeviation", "skewness", "kurtosis")

  implicit def DecodeStats: DecodeJson[funnel.Stats] =
    jdecode6L((last: Option[Double], mean: Double, count: Double, variance: Double,
               skewness: Double, kurtosis: Double) => {
      val m0 = count.toLong
      val m1 = mean
      // need to reverse the math to compute m2, m3, m4 from variance, skewness, kurtosis
      // see: https://github.com/twitter/algebird/blob/develop/algebird-core/src/main/scala/com/twitter/algebird/MomentsGroup.scala
      val m2 = variance * count
      val m3 = skewness / math.sqrt(count) * math.pow(m2, 1.5)
      val m4 = (kurtosis + 3)/count * math.pow(m2, 2)
      new funnel.Stats(com.twitter.algebird.Moments(m0, m1, m2, m3, m4), last)
    })("last", "mean", "count", "variance", "skewness", "kurtosis")

  implicit def EncodeReportable[A:Reportable]: EncodeJson[A] = EncodeJson {
    case a: Double => encodeResult(a)
    case a: Boolean => encodeResult(a)
    case a: String => encodeResult(a)
    case a: funnel.Stats => encodeResult(a)
    case h => sys.error("unsupported reportable: " + h)
  }

  implicit def DecodeReportable(r: Reportable[Any]): DecodeJson[Any] = DecodeJson { c =>
    r match {
      case Reportable.B => c.as[Boolean] ||| D.fail("expected Boolean", c.history)
      case Reportable.D => c.as[Double] ||| D.fail("expected Double", c.history)
      case Reportable.Stats => c.as[funnel.Stats] ||| D.fail("expected Stats", c.history)
      case Reportable.S => c.as[String] ||| D.fail("expected String", c.history)
    }
  }

  implicit def EncodeReportableT[A]: EncodeJson[Reportable[A]] =
    jencode1((r: Reportable[A]) => r.description)

  implicit def DecodeReportableT: DecodeJson[Reportable[Any]] = DecodeJson { c =>
    c.as[String].flatMap { s =>
      Reportable.fromDescription(s).map(D.ok)
                .getOrElse(D.fail("invalid type: " + s, c.history))
    }
  }

  implicit def EncodeDatapoint[A]: EncodeJson[Datapoint[A]] =
    jencode2L((d: Datapoint[A]) => (d.key, EncodeReportable(d.key.typeOf)(d.value)))("key", "value")

  implicit def DecodeDatapoint: DecodeJson[Datapoint[Any]] = DecodeJson { c => for {
    k <- (c --\ "key").as[Key[Any]](DecodeKey)
    v <- (c --\ "value").as[Any](DecodeReportable(k.typeOf))
  } yield Datapoint(k, v) }

  case class Audit(prefix: String, count: Int)

  implicit def EncodeAudit: EncodeJson[Audit] =
    EncodeJson((b: Audit) =>
      ("prefix"    := b.prefix) ->:
      ("count"     := b.count) ->:
      jEmptyObject
    )

}

