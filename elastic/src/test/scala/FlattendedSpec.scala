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

import org.scalatest.{FlatSpec,Matchers}

class FlattenedSpec extends FlatSpec with Matchers {
  val F = ElasticFlattened(Monitoring.default)

  def point =
    Datapoint[Any](Key[Double](s"now/${java.util.UUID.randomUUID.toString}", Units.Count, "some description",
      Map(AttributeKeys.source -> "http://10.0.10.10/stream/now", AttributeKeys.kind -> "gauge")
    ), 3.14)

  def point2 =
    Datapoint[Any](Key[Stats](s"sliding/${java.util.UUID.randomUUID.toString}", Units.Count, "some description",
      Map(AttributeKeys.source -> "http://10.0.10.10/stream/now", AttributeKeys.kind -> "gauge")
    ), Stats(3.14))

  def point3 =
    Datapoint[Any](Key[Stats](s"sliding/file_system.%2F.use_percent", Units.Bytes(Units.Base.Kilo), "some description",
      Map(AttributeKeys.source -> "http://10.0.10.10/stream/now", AttributeKeys.kind -> "gauge")
    ), Stats(3.14))

  // TIM: christ, we need more tests here. sorry!

  "toJson" should "correctly render documents to json in the happy case" in {
    println {
      F.toJson("dev", "127.0.0.1", "local")(point3)
    }
  }
}