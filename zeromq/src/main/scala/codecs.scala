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
package telemetry

import scodec._
import scalaz.\/
import scodec.bits._
import shapeless.{Sized,Lazy}
import java.util.concurrent.TimeUnit

trait Codecs {
  implicit val utf8: Codec[String] = codecs.variableSizeBytes(codecs.int32, codecs.utf8)

  implicit val uint8 = codecs.uint8

  implicit def map[K,V](implicit K: Codec[K], V: Codec[V]): Codec[Map[K,V]] =
    codecs.vector[(K,V)](K ~ V).xmap[Map[K,V]](
      _.toMap,
      _.toVector
    )
}

trait KeyCodecs extends Codecs { self =>
  import TimeUnit._

  val keyEncode: Encoder[Key[Any]] = new Encoder[Key[Any]] {
    override def sizeBound = SizeBound.unknown

    def encode(k: Key[Any]): scodec.Attempt[BitVector] = {
      for {
        b1 <- k.typeOf match {
          case Reportable.B => uint8.encode(1)
          case Reportable.D => uint8.encode(2)
          case Reportable.S => uint8.encode(3)
          case Reportable.Stats => uint8.encode(4)
        }

        b2 <- utf8.encode(k.name)
        b3 <- unitsCodec.encode(k.units)
        b4 <- utf8.encode(k.description)
        b5 <- self.map[String,String].encode(k.attributes)
      } yield {
        b1 ++ b2 ++ b3 ++ b4 ++ b5
      }
    }
  }

  val keyDecode: Decoder[Key[Any]] = new Decoder[Key[Any]] {
    def decode(bits: BitVector): scodec.Attempt[DecodeResult[Key[Any]]] = {
      uint8.decode(bits) flatMap {
        case DecodeResult(disc,bits) =>
          val reportable = disc match {
            case 1 => Reportable.B
            case 2 => Reportable.D
            case 3 => Reportable.S
            case _ => Reportable.Stats
          }
          _decodeKey(reportable).decode(bits)
      }
    }
  }


  val datapointEncode: Encoder[Datapoint[Any]] = new Encoder[Datapoint[Any]] {
    override def sizeBound = SizeBound.unknown
    def encode(d: Datapoint[Any]): Attempt[BitVector] = {
      for {
        bk <- keyEncode.encode(d.key)
        bv <- d.key.typeOf match {
          case Reportable.B => codecs.bool.encode(d.value.asInstanceOf[Boolean])
          case Reportable.D => codecs.double.encode(d.value.asInstanceOf[Double])
          case Reportable.S => utf8.encode(d.value.asInstanceOf[String])
          case Reportable.Stats => statsCodec.encode(d.value.asInstanceOf[Stats])
        }
      } yield bk ++ bv
    }
  }

  val datapointDecode: Decoder[Datapoint[Any]] = new Decoder[Datapoint[Any]] {
    def decode(bits: BitVector): Attempt[DecodeResult[Datapoint[Any]]] = {
      uint8.decode(bits) flatMap {
        case DecodeResult(disc,bits) =>
          disc match {
            case 1 =>
              for {
                key_ <- _decodeKey(Reportable.B).decode(bits)
                DecodeResult(key, bits2) = key_

                v_ <- codecs.bool.decode(bits2)
                DecodeResult(v,bits3) = v_
              } yield DecodeResult(Datapoint(key,v), bits3)

            case 2 =>
              for {
                key_ <- _decodeKey(Reportable.D).decode(bits)
                DecodeResult(key, bits2) = key_

                v_ <- codecs.double.decode(bits2)
                DecodeResult(v,bits3) = v_
              } yield DecodeResult(Datapoint(key,v), bits3)

            case 3 =>
              for {
                key_ <- _decodeKey(Reportable.S).decode(bits)
                DecodeResult(key, bits2) = key_

                v_ <- utf8.decode(bits2)
                DecodeResult(v,bits3) = v_
              } yield DecodeResult(Datapoint(key,v),bits3)

            case _ =>
              for {
                key_ <- _decodeKey(Reportable.Stats).decode(bits)
                DecodeResult(key, bits2) = key_

                v_ <- statsCodec.decode(bits2)
                DecodeResult(v,bits3) = v_
              } yield DecodeResult(Datapoint(key,v),bits3)
          }
      }
    }
  }


  private def _decodeKey(reportable: Reportable[Any]): Decoder[Key[Any]] = new Decoder[Key[Any]] {
    def decode(bits: BitVector): Attempt[DecodeResult[Key[Any]]] = {
      for {
        name_ <- utf8.decode(bits)
        DecodeResult(name, bits1) = name_

        units_ <- unitsCodec.decode(bits1)
        DecodeResult(units, bits2) = units_

        desc_ <- utf8.decode(bits2)
        DecodeResult(desc, bits3) = desc_

        attributes_ <- self.map[String,String].decode(bits3)
        DecodeResult(attributes, bits4) = attributes_

      } yield DecodeResult(Key(name, reportable, units, desc, attributes),bits4)
    }
  }


  implicit val statsCodec: Codec[Stats] = (
    codecs.optional(codecs.bool, codecs.double) ~
      codecs.double ~
      codecs.uint32L ~
      codecs.double ~
      codecs.double ~
      codecs.double ~
      codecs.double).xmap(
    {case ((((((last,mean),count),variance),standardDeviation),skewness),kurtosis) =>
      val m0 = count.toLong
      val m1 = mean
      val m2 = variance * count
      val m3 = skewness / math.sqrt(count) * math.pow(m2, 1.5)
      val m4 = (kurtosis + 3)/count * math.pow(m2, 2)
      new funnel.Stats(com.twitter.algebird.Moments(m0, m1, m2, m3, m4), last)
    },
    { stats => ((((((stats.last, stats.mean),
                    stats.count),
                   stats.variance),
                  stats.standardDeviation),
                 stats.skewness),
                stats.kurtosis)}
  )

  lazy implicit val baseCodec: Codec[Units.Base] = {
    import Units.Base._
      (codecs.provide(Zero) :+:
       codecs.provide(Kilo) :+:
       codecs.provide(Mega) :+:
       codecs.provide(Giga)).discriminatedByIndex(uint8).as[Units.Base]
  }

  val tuToInt: TimeUnit => Int = _ match {
      case DAYS => 0
      case HOURS => 1
      case MICROSECONDS => 2
      case MILLISECONDS => 3
      case MINUTES => 4
      case NANOSECONDS => 5
      case SECONDS => 6
  }
  val intToTU: Int => TimeUnit = _ match {
      case 0 => DAYS
      case 1 => HOURS
      case 2 => MICROSECONDS
      case 3 => MILLISECONDS
      case 4 => MINUTES
      case 5 => NANOSECONDS
      case 6 => SECONDS
  }

  implicit val timeUnitCodec: Codec[TimeUnit] = codecs.uint8.xmap(intToTU, tuToInt)

  implicit def unitsCodec: Codec[Units] = {
    import Units._
    (Codec[Duration] :+:
       Codec[Bytes] :+:
       codecs.provide(Count) :+:
       codecs.provide(Ratio) :+:
       codecs.provide(TrafficLight) :+:
       codecs.provide(Healthy) :+:
       codecs.provide(Load) :+:
       codecs.provide(None)).discriminatedByIndex(uint8).as[Units]
  }
}
