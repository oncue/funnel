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
package examples

import org.scalacheck._
import Prop._
import scala.concurrent.duration._

object Example {

  def main(args: Array[String]): Unit = {

    object metrics {
      import Metric._
      import funnel.instruments._

      // Get the Scalaz syntax for the |@| operation
      // (idiomatic function application)
      import scalaz.syntax.applicative._

      val reqs = counter("requests#")
      val dbOk = gauge("db-up?", true)
      val query = timer("query-speed")

      // verify that we haven't received more than 20k req,
      // the db is up, and our average query response time
      // is under 20 millis
      val healthy: Metric[Boolean] =
        (reqs.keys |@| dbOk.keys |@| query.keys) { (n, db, t) =>
          n < 20000 &&
          db &&
          t.mean < 20
        }

       healthy.publish("healthy", Units.Healthy).run
    }

    metrics.query.time {
      if (true) metrics.dbOk.set(true)
      metrics.reqs.increment
      metrics.reqs.incrementBy(10)
    }
  }
}
