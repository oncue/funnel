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

import org.scalatest._
import scalaz.\/, \/._
import InstrumentKinds._

class ParserSpec extends FlatSpec with Matchers {
  "Parser.toMetric" should "interpret metric strings" in {

    Parser.toMetric("foo:123|g") should equal (
      right(ArbitraryMetric("foo",GaugeDouble,Some("123.0"))) )

    Parser.toMetric("bar:567|c") should equal (
      right(ArbitraryMetric("bar",Counter,Some("567.0"))) )

    Parser.toMetric("bar:567|c|@2.0") should equal(
      right(ArbitraryMetric("bar",Counter,Some("283.5"))) )

    Parser.toMetric("bar:xxxx|c|@2.0").isLeft should equal(true)

    Parser.toMetric("bar:1234|c") should equal(
      right(ArbitraryMetric("bar",Counter,Some("1234.0"))) )

    Parser.toMetric("aa.local-1433471858524.driver.jvm.pools.Compressed-Class-Space.usage:0.01|g") should equal(
      right(ArbitraryMetric("aa.local-1433471858524.driver.jvm.pools.Compressed-Class-Space.usage",GaugeDouble,Some("0.01"))) )

    Parser.toMetric("aa.local-1433471858524.driver.jvm.pools.Compressed-Class-Space.usage:234.23456|g") should equal(
      right(ArbitraryMetric("aa.local-1433471858524.driver.jvm.pools.Compressed-Class-Space.usage",GaugeDouble,Some("234.23"))) )
  }

  it should "handle the stuff stew is getting from some ghetto kafka statsd exporter" in {
    Parser.toMetric("kafka.kafka.network.RequestMetrics.request.LeaderAndIsr.TotalTimeMs.p50:2.50|ms") should equal(
      right(ArbitraryMetric("kafka.kafka.network.RequestMetrics.request.LeaderAndIsr.TotalTimeMs.p50", Timer, Some("2.5 milliseconds"))))
  }

  it should "handle trailing whitespace" in {
    Parser.toMetric("foo:123|g\r\n") should equal (
      right(ArbitraryMetric("foo",GaugeDouble,Some("123.0"))) )
    Parser.toMetric("foo:123|g\n") should equal (
      right(ArbitraryMetric("foo",GaugeDouble,Some("123.0"))) )
    Parser.toMetric("foo:123|g ") should equal (
      right(ArbitraryMetric("foo",GaugeDouble,Some("123.0"))) )
    Parser.toMetric("foo:123|g\r \n \r\n  \r \t \n") should equal (
      right(ArbitraryMetric("foo",GaugeDouble,Some("123.0"))) )
  }
}
