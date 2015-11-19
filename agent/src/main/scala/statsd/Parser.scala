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
package agent
package statsd

import util.matching.Regex
import scalaz.\/
import journal.Logger

object Parser {

  private[this] val log = Logger[Parser.type]
  // borrowed this from the bsd work here:
  // https://github.com/mojodna/metricsd
  private[statsd] val matcher =
    new Regex("""([^:]+)(:((-?\d+(?:\.?\d*)|delete)?(\|((\w+)(\|@(\d+\.\d+))?)?)?)?)?""")

  def toRequest(line: String): String => Throwable \/ InstrumentRequest =
    cluster => toMetric(line).map(InstrumentRequest(cluster, _))

  def toMetric(line: String): Throwable \/ ArbitraryMetric =
    for {
      a <- fromString(line)
      b <- toInstrumentKind(a._3)
      c <- toSampleRate(a._4)
      d <- toValue(a._2, c)
      dd = if(b == InstrumentKinds.Timer) s"$d milliseconds" else d.toString
  } yield ArbitraryMetric(a._1,b,Option(dd))



  def fromString(line: String): Throwable \/ (String,String,String,String) = {
    \/.fromTryCatchNonFatal {
      log.warn("fromString: <" + line + ">")
      val matcher(name,_,_,value,_,_,kind,_, sampleRate) = line.trim
      (name, value, kind, sampleRate)
    }.leftMap(err => new RuntimeException(s"Unable to parse input. Check the formating and ensure you are using valid statsd syntax. Error was: $err"))
  }

  def toInstrumentKind(s: String): Throwable \/ InstrumentKind = {
    s.trim match {
      case "c"  => \/.right(InstrumentKinds.Counter)
      case "ms" => \/.right(InstrumentKinds.Timer)
      case "g"  => \/.right(InstrumentKinds.GaugeDouble)
      case "m"  => \/.right(InstrumentKinds.Counter)
      case _   => \/.left(new Exception("String did not convert to a valid instrument kind."))
    }
  }

  def toValue(s: String, rate: Double): Throwable \/ Double = {
    for {
      a <- \/.fromTryCatchNonFatal(s.trim.toLowerCase)
      _ <- if(a == "delete") \/.left(new Exception("Deletion is not supported.")) else \/.right(a)
      b <- \/.fromTryCatchNonFatal(a.toDouble)
    } yield BigDecimal(b * 1 / rate).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble
  }

  private[statsd] def toSampleRate(s: String): Throwable \/ Double =
    Option(s).map(_.toDouble).map(\/.right(_))
      .getOrElse(\/.right(1.0))

}
