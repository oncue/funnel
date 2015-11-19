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

import org.scalatest.{FlatSpec,Matchers,BeforeAndAfterAll}
import scala.concurrent.{Future,Await}
import scala.concurrent.duration._
import dispatch._, Defaults._

class ServerSpec extends FlatSpec with Matchers with BeforeAndAfterAll {

  val platform = new TestPlatform {
    val config = new TestConfig
  }
  val core = new TestChemist

  val http = Http()

  private def fetch(path: String): String =
    Await.result(http(url(s"http://127.0.0.1:64523$path") OK as.String), 5.seconds)

  override def beforeAll(): Unit = {
    Future(Server.unsafeStart(new Server(core,platform)))
    Thread.sleep(1.seconds.toMillis)
  }

  override def afterAll(): Unit = {
  }

  behavior of "chemist server"

  it should "respond to index.html" in {
    fetch("/index.html").length > 10
  }
}
