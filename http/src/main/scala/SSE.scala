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
package http

import argonaut.{DecodeJson, EncodeJson}
import java.io.InputStream
import java.util.concurrent.ExecutorService
import java.net.{URL,URI}
import scalaz.concurrent.Task
import scalaz.stream._
import scalaz.stream.{Process => P}
import scalaz.stream.async.mutable.{Queue,Signal}

object SSE {

  import JSON._

  def dataEncode[A](a: A)(implicit A: EncodeJson[A]): String =
    "data: " + A(a).nospaces.replace("\n", "\ndata: ")

  /**
   * Write a server-side event stream (http://www.w3.org/TR/eventsource/)
   * of the given metrics to the `Writer`. This will block the calling
   * thread indefinitely.
   */
  def writeEvents(events: Process[Task, Datapoint[Any]],
                  sink: java.io.Writer): Unit =
    events.map(kv => s"event: reportable\n${dataEncode(kv)(EncodeDatapoint[Any])}\n")
          .intersperse("\n")
          .flatMap(writeTo(sink))
          .run.run

  /**
   * Write a server-side event stream (http://www.w3.org/TR/eventsource/)
   * of the given keys to the given `Writer`. This will block the calling
   * thread indefinitely.
   */
  def writeKeys(events: Process[Task, Key[Any]], sink: java.io.Writer): Unit =
    events.map(k => s"event: key\n${dataEncode(k)}\n")
          .intersperse("\n")
          .map(writeTo(sink))
          .run.run

  private def writeTo(sink: java.io.Writer): String => Process[Task, Unit] =
    line => Process.eval(Task {
      sink.write(line)
      sink.flush // this is a line-oriented protocol,
                 // so we flush after each line, otherwise
                 // consumer may get delayed messages
    }.attempt).flatMap(_.fold(e => e match {
      case x: java.io.IOException =>
      // when client disconnects we'll get a broken pipe
      // IOException from the above `sink.write`. This
      // gets translated to normal termination
      Process.halt
    }, x => Process.emit(x)))

  /// parsing

  case class ParseError(message: String) extends Exception(message)

  /**
   * Streaming parser for an SSE stream. Example, given:
   *
   * {{{
   *   event: blah
   *   data: uno
   *   data: dos
   *
   *   event: other-event
   *   data: tres
   * }}}
   *
   * It will emit ("blah", "uno\ndos"), ("other-event","tres")
   *
   * This parser is rather brittle, and doesn't implement all the
   * various features of arbitrary SSE streams, described here:
   * http://www.w3.org/TR/2012/WD-eventsource-20120426/ In the
   * event of any unexpected formatting, raises a `ParseError`
   * within the returned `Process`.
   */
  def blockParser: Process1[String, (String,String)] = {
    def awaitingEvent: Process1[String,(String,String)] =
      P.await1[String].flatMap { line =>
        if (line.forall(_.isWhitespace)) awaitingEvent
        else if (line.startsWith(":")) awaitingEvent
        else {
          val (k,v) = parseLine(line)
          if (k != "event") throw ParseError(s"expected 'event', got $k")
          collectingData(v, new StringBuilder)
        }
      }
    def collectingData(event: String, buf: StringBuilder):
    Process1[String,(String,String)] =
      P.await1[String].flatMap { line =>
        if (line.forall(_.isWhitespace)) P.emit(event -> buf.toString) ++ awaitingEvent
        else {
          val (k, v) = parseLine(line)
          if (k != "data") throw ParseError("expected 'data'")
          val nl = if (buf.isEmpty) "" else "\n"
          collectingData(event, buf.append(v).append(nl))
        }
      }
    awaitingEvent
  }

  // "data: blah" -> ("data", "blah")
  // "foo: bar" -> ("foo", "bar")
  def parseLine(line: String): (String, String) = {
    val c = line.indexOf(':')
    if (c == -1) (line, "")
    else {
      val hd = line.substring(0, c)
      val tl = line.drop(c+1).trim
      (hd, tl)
    }
  }

  /**
   * Return a stream of all events from the given URL.
   * Example: `readEvents("http://localhost:8001/stream/sliding/jvm")`.
   */
  def readEvents(uri: URI)(implicit S: ExecutorService = Monitoring.serverPool):
      Process[Task, Datapoint[Any]] = {
    urlLinesR(uri.toURL)(S).attempt().pipeO(blockParser.map {
      case (_,data) => parseOrThrow[Datapoint[Any]](data)
                                            }).flatMap(_.fold({e => Process.fail(e)
                                                              }, {s => Process.emit(s)
                                                              }))
  }

  // various helper functions

  def parseOrThrow[A:DecodeJson](s: String): A =
    argonaut.Parse.decodeEither[A](s).fold(e => throw ParseError(e), identity)

  def urlDecode[A:DecodeJson](url: URL)(implicit S: ExecutorService = Monitoring.serverPool): Task[A] =
    urlFullR(url)(S).map(parseOrThrow[A])

  def urlLinesR(url: URL)(implicit S: ExecutorService = Monitoring.serverPool): Process[Task, String] =
    Process.suspend {
      try linesR(url.openStream)(S)
      catch { case e: Throwable => Process.fail(e) }
    }

  def urlFullR(url: URL)(implicit S: ExecutorService = Monitoring.serverPool): Task[String] =
    urlLinesR(url)(S).chunkAll.map(_.mkString("\n")).runLastOr("")

  /**
   * Adapted from scalaz-stream, but this version is nonblocking.
   */
  def linesR(in: InputStream)(implicit S: ExecutorService = Monitoring.serverPool): Process[Task,String] =
    io.resource(Task(scala.io.Source.fromInputStream(in))(S))(
             src => Task(src.close)(S)) { src =>
      lazy val lines = src.getLines // A stateful iterator
      Task { if (lines.hasNext) lines.next else throw Cause.Terminated(Cause.End) } (S)
    }
}
