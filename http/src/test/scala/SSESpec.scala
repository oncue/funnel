package funnel
package http

import com.twitter.algebird.Group
import org.scalacheck.Arbitrary._
import org.scalacheck.Prop._
import org.scalacheck._

import scalaz.concurrent.Task._
import scalaz._
import Scalaz._
import java.io.InputStream

import scalaz.concurrent.Task
import scalaz.stream.Process

object SSESpec extends Properties("SSE") {

  property("url parse process propagates Exceptions") = secure {
    val x: Process[Task, String] = Process.eval{Task { throw new RuntimeException("boom")}}
    SSE.readUrl(x)
    x.attempt().runLast.unsafePerformSync match {
      case Some(x) if (x.isLeft) => true
      case _ => false
    }
  }
}
