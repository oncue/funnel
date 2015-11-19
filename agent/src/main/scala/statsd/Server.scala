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
import java.net.InetSocketAddress
// import java.util.concurrent.Executors
import io.netty.bootstrap.Bootstrap
import io.netty.channel.{ChannelOption,EventLoopGroup}
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioDatagramChannel

object Server {
  // never explicitly specify "host" as a paramater here.
  // non-root users cannot accept SO_BROADCAST, so instead,
  // we simply use a wildcard address binding.
  // more information here:
  // https://github.com/netty/netty/issues/576
  def apply(port: Int, prefix: String)(I: Instruments): Task[Unit] =
    Task {
      val group = new NioEventLoopGroup
      try {
        val b = new Bootstrap
        b.group(group)
          .channel(classOf[NioDatagramChannel])
          .option[java.lang.Boolean](ChannelOption.SO_BROADCAST, true)
          .handler(new Handler(prefix,I))

        b.bind(new InetSocketAddress(port))
         .sync()
         .channel()
         .closeFuture()
          .await()
        ()
      } finally group.shutdownGracefully()
    }(Monitoring.serverPool)
}
