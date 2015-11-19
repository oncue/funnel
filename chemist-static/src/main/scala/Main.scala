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
package static

import java.io.File
import journal.Logger
import concurrent.duration._
import scalaz.concurrent.Task
import http.MonitoringServer
import knobs.{FileResource,ClassPathResource,Optional,Pattern,Required}

object Main {
  def main(args: Array[String]): Unit = {
    val chemist = new StaticChemist[Static]

    val k = knobs.load(
      Required(ClassPathResource("oncue/chemist.cfg")) ::
      Optional(FileResource(new File("/usr/share/oncue/etc/chemist.cfg"))) :: Nil).run

    val s = new Static { val config = Config.readConfig(k).run }

    // TIM: best I can tell, this didnt actually work beforehand
    // as we're running this on a  prototype and it doesn't
    // seem to reload.
    // k.subscribe(Pattern("*.*"), {
    //   case (name, optvalue) => chemist.bootstrap.run(new Static {
    //     val config = Config.readConfig(k).run
    //   })
    // })

    val monitoring = MonitoringServer.start(Monitoring.default, 5775)

    // this is the edge of the world and will just block until its stopped
    Server.unsafeStart(new Server(chemist, s))

    // if we reach these then the server process has stopped and we need
    // to cleanup the associated resources.
    monitoring.stop()
    dispatch.Http.shutdown()
  }
}
