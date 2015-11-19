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

import java.net.URI
import java.util.concurrent.{ExecutorService,ScheduledExecutorService}
import scalaz.stream.async.mutable.{Queue, Signal}
import scalaz.stream.{Process,Process1}
import scalaz.concurrent.Task

object Mirror {
  import sockets._

  /**
   * Given that chemist has no idea what particular version of the funnel suite
   * protocols the target might be running (and keeping deployment information
   * and normal source in sync is potentially fragile / human process) we instead
   * opt for a path where this module will attempt to listen for the specified
   * path on all ZMTP protocol versions it is aware of.
   */
  private[zeromq] def parseURI(u: URI): List[Array[Byte]] = {
    val (win,top) = u.getPath.split('/') match {
      case Array(_, w) =>
        (Windows.fromString(w),None)

      case Array(_, w, topic@_*) =>
        (Windows.fromString(w), Option(Topic(topic.mkString("/"))))
    }

    // this whole thing is a bit of a hack, but it
    // saves duplicting the header parsing code.
    Versions.supported.toList.map { v =>
      Transported(
        serial = 0, // this will be ignored
        scheme = Schemes.fsm,
        version = v,
        window = win,
        topic = top,
        bytes = Array.empty[Byte]
      ).header.getBytes("ASCII")
    }
  }

  def from(alive: Signal[Boolean])(uri: URI): Process[Task, Datapoint[Any]] = {
    val discriminator = parseURI(uri)
    val t = if(discriminator.isEmpty) topics.all else topics.specific(discriminator)
    Endpoint(subscribe &&& (connect ~ t), uri)
      .fold(Process.fail, l => Ø.link(l)(alive)(Ø.receive).pipe(fromTransported))
  }

  val fromTransported : Process1[Transported, Datapoint[Any]] = Process.receive1 { (t:Transported) =>
    import http.JSON._, http.SSE
    t.version match {
      case Versions.v1 =>
        try Process.emit(SSE.parseOrThrow[Datapoint[Any]](new String(t.bytes)))
        catch {
          case e: Exception => Process.fail(e)
        }
      case Versions.v2 => sys.error("not implemented yet!")
    }
  } ++ fromTransported
}
