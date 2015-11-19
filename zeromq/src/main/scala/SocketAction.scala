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

import org.zeromq.ZMQ, ZMQ.Context, ZMQ.Socket
import scalaz.concurrent.Task

abstract class SocketAction extends (Socket => Location => Task[Socket]){ self =>
  def ~(f: Socket => Task[Unit]): SocketAction =
    new SocketAction {
      def apply(s: Socket): Location => Task[Socket] = location =>
        for {
          a <- self.apply(s)(location)
          _ <- f(s)
        } yield s
    }
}

trait SocketActions {
  object connect extends SocketAction { self =>
    def apply(s: Socket): Location => Task[Socket] =
      location => Task.delay {
        s.connect(location.uri.toString)
        s.setReceiveTimeOut(-1) // wait forever; this is desired.
        s
      }
  }

  object bind extends SocketAction {
    def apply(s: Socket): Location => Task[Socket] =
      location => Task.delay { s.bind(location.uri.toString); s }
  }

  /**
   * Be sure to remember that ZeroMQ matches incoming messages based
   * on the Array[Byte] of the message, so the header itself must be
   * static for every message in the first few bytes in order to
   * discriminate one message "topic" from another.
   */
  object topics {
    def specific(discriminator: List[Array[Byte]]): Socket => Task[Unit] =
      s => Task.delay(discriminator.foreach(s.subscribe))

    def all: Socket => Task[Unit] =
      specific(List(Array.empty[Byte]))
  }
}
