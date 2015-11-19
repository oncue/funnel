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

abstract class SocketMode(val asInt: Int) extends (Context => Task[Socket]){
  def apply(ctx: Context): Task[Socket] =
    Task.delay(ctx.socket(asInt))

  def &&&(f: Socket => Location => Task[Socket]): SocketBuilder =
    ctx => location => apply(ctx).flatMap(socket => f(socket)(location))
}

trait SocketModes {
  object push extends SocketMode(ZMQ.PUSH)
  object pull extends SocketMode(ZMQ.PULL)
  object publish extends SocketMode(ZMQ.PUB)
  object subscribe extends SocketMode(ZMQ.SUB)
}
