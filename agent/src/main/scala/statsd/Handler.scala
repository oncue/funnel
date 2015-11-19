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
package agent
package statsd

import scalaz.concurrent.Task
import io.netty.channel.{SimpleChannelInboundHandler,ChannelHandlerContext}
import io.netty.channel.socket.DatagramPacket
import io.netty.util.CharsetUtil

class Handler(prefix: String, I: Instruments) extends SimpleChannelInboundHandler[DatagramPacket]{
  override def channelRead0(ctx: ChannelHandlerContext, packet: DatagramPacket){
    val msg: String = packet.content.toString(CharsetUtil.UTF_8)
    msg.trim.split("\n").foreach { line: String =>
      if(line.trim.length > 0) {
        val task = Parser.toRequest(line)(prefix).fold(
          a => Task.fail(a),
          b => RemoteInstruments.metricsFromRequest(b)(I))

        task.run // unsafe!
      }
    }
  }

  override def channelReadComplete(ctx: ChannelHandlerContext) {
    ctx.flush()
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
    cause.printStackTrace()
    // We don't close the channel because we can keep serving requests.
  }
}
