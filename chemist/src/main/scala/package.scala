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

package object chemist {
  type HostAndPort = String

  import scalaz.{\/,Order}
  import scalaz.std.string._
  import scalaz.concurrent.Task
  import java.net.URI
  import concurrent.{Future,ExecutionContext}

  implicit val uriOrder: Order[URI] = Order[String].contramap[URI](_.toString)

  implicit class DistributionSyntax(d: Sharding.Distribution){
    def hasFlasks: Boolean = d.keySet.nonEmpty
  }

  implicit def fromScalaFuture[A](a: Future[A])(implicit e: ExecutionContext): Task[A] =
    Task async { k =>
      a.onComplete {
        t => k(\/.fromTryCatchThrowable[A,Exception](t.get)) }}
}
