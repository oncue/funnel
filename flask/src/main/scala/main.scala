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
package flask

import java.io.File
import journal.Logger
import scalaz.std.option._
import scala.concurrent.duration._
import scalaz.syntax.applicative._
import knobs.{Config,ClassPathResource,FileResource,Required,Optional}

object Main {
  def main(args: Array[String]): Unit = {
    val log = Logger[Main.type]

    /**
     * Accepting argument on the command line is really just a
     * convenience for testing and ad-hoc ops trial of the agent.
     *
     * Configs are loaded in order; LAST WRITER WINS, as configs
     * are reduced right to left.
     */
    val options: Options = (for {
      a <- knobs.loadImmutable(
        List(
          Required(ClassPathResource("flask/defaults.cfg")),
          Optional(FileResource(new File("/usr/share/oncue/etc/flask.cfg")))
        ) ++ args.toList.map(p => Optional(FileResource(new File(p)))))
    } yield Options.readConfig(a)).run

    log.debug(s"loaded the following configuration settings: $options")

    val I = new Instruments()

    val app = new Flask(options, I)

    app.unsafeRun()
  }
}
