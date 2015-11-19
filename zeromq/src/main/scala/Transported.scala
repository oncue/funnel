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
package zeromq

/**
 * A payload for transmitting via a ∅ socket
 */
case class Transported(
  /** every message should have a unique serial which is used by recipient to track message order */
  serial: Serial,
  /** the scheme by which this packet was encoded */
  scheme: Scheme,
  /** The version of the scheme */
  version: Version,
  /** The version of the scheme */
  window: Option[Window],
  /** An optional "topic" for this packet, which can be used for filtering subscriptions */
  topic: Option[Topic],
  /** The actual payload */
  bytes: Array[Byte]
){
  /**
   * construct the String for the header packet
   */
  def header: String = (window,topic) match {
    case (None, None)       => s"$scheme/$version"
    case (Some(w), None)    => s"$scheme/$version/$w"
    case (None, Some(t))    => s"$scheme/$version/${Windows.unknown}/$t"
    case (Some(w), Some(t)) => s"$scheme/$version/$w/$t"
  }
}

object Transported {
  import Versions._

  val P = new scalaparsers.Parsing[Unit] {}
  import scalaparsers.ParseState
  import scalaparsers.Supply
  import scalaparsers.Pos
  import scalaparsers.Err
  import P._

  val slash = ch('/')
  val notSlash: Parser[String] = satisfy(_ != '/').many map(_.mkString)

  val scheme: Parser[Scheme] = notSlash map Schemes.fromString
  val version: Parser[Version] = notSlash map Versions.fromString
  val window: Parser[Option[Window]] = notSlash map Windows.fromString

  // this is overly complicated in order to handle the case when we have a topic in an unknown window
  val topic: Parser[Option[Topic]] =
    slash.optional flatMap {
      case None => unit(None)
      case Some(s) => (notSlash map {t => Some(Topic(t))}).orElse(Some(Topic("")))
    }

  val windowTopic: Parser[(Option[Window], Option[Topic])] =
    (slash.optional).flatMap {
      case None => unit((None, None))
      case Some(_) => for {
        w <- window
        t <- topic
      } yield(w -> t)
    }

  val headerParse: Parser[Array[Byte] => Serial => Transported] =
    for {
      s <- scheme << slash
      v <- version
      wt <- windowTopic
    } yield (bytes => serial => Transported(serial, s, v, wt._1, wt._2, bytes))

  /**
   * Reconstructed the Transported instance on the receive side given the header and payload
   */
  def apply(h: String, serial: Serial, bytes: Array[Byte]): Transported = {
    headerParse.run(ParseState(
                      loc = Pos.start("header", h),
                      input = h,
                      s = (),
                      layoutStack = List()), Supply.create) match {
      case Left(e)  => Transported(serial, Schemes.unknown, Versions.unknown, None, None, bytes)
      case Right(f) => f._2(bytes)(serial)
    }
  }
}

/**
 * A typeclass for preparing an A for being written to a ∅ socket, It
 * needs to be able to produce a Transportable object for any instance
 * of A
 */
abstract class Transportable[A] {
  def apply(a: A, serial: Serial): Transported
}

object Transportable {
  // create a Transportable from a (A,Serial) => Transported
  def apply[A](f: (A,Serial) => Transported): Transportable[A] =
    new Transportable[A] { def apply(a: A, s: Serial) = f(a,s) }
}
