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
package object zeromq {
  import scalaz.concurrent.Task
  import org.zeromq.ZMQ.{Context,Socket}
  import scalaz.stream.async.mutable.Signal

  type SocketBuilder = org.zeromq.ZMQ.Context => Location => Task[Socket]
  type Serial = Int

  val Ø = ZeroMQ

  object sockets extends SocketActions with SocketModes

  private[zeromq] def stop(signal: Signal[Boolean]): Task[Unit] = {
    for {
      _ <- signal.set(false)
      _ <- signal.close
    } yield ()
  }

  private[zeromq] def errorHandler: PartialFunction[Throwable,Task[Socket]] = {
    case e: java.io.FileNotFoundException => {
      Ø.log.error("Unable to bind to the spcified file location. "+
                  "Please ensure the path to the file you're writing actually exists.")
      Task.fail(e)
    }
    case e: Exception => {
      Ø.log.error(s"Unable to configure the specified socket. Error: ${e.getMessage}")
      e.printStackTrace()
      Task.fail(e)
    }
  }
}
