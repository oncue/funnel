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

import scalaz.concurrent.Task

/** URL decoding extractor for strings */
object Decode {
  import java.net.URLDecoder
  import java.nio.charset.Charset

  trait Extract {
    def charset: Charset
    def apply(raw: String): Option[String] =
      unapply(raw)
    def unapply(raw: String): Option[String] =
      Task.delay(URLDecoder.decode(raw, charset.name())).attempt.map(_.toOption).run
  }

  /** Extract a string from a UTF8 URL-encoded string */
  object utf8 extends Extract {
    val charset = Charset.forName("utf8")
  }
}
