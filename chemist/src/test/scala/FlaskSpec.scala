package funnel.chemist

import org.scalatest.{Matchers, FlatSpec}
import Fixtures._

class FlaskSpec extends FlatSpec with Matchers {

  private val t = LocationTemplate(s"http://@host:@port/mirror/sources")

  val fakeHttp = FakeHttpExecutor(
    Map(
      flask01.location.uriFromTemplate(t).toString ->
        """
          |[
          |  {"cluster":"app1-7.0.317","uris":["http://ip-10-123-214-77.ec2.internal:5775/stream/now?type=%22String%22"]},
          |  {"cluster":"app2-3.0.91", "uris":["http://ip-10-123-216-55.ec2.internal:5775/stream/now?type=%22String%22"]}
          |]
        """.stripMargin,
      flask02.location.uriFromTemplate(t).toString ->
        """
          |[
          |  {"cluster":"app3-2.1.1","uris":["http://ip-10-123-215-88.ec2.internal:5775/stream/now?type=%22String%22"]},
          |  {"cluster":"app4-1.1.1","uris":["http://ip-10-123-215-99.ec2.internal:5775/stream/now?type=%22String%22"]},
          |  {"cluster":"app5-3.0.92","uris":["http://ip-10-123-216-66.ec2.internal:5775/stream/now?type=%22String%22"]}
          |]
        """.stripMargin,
      flask03.location.uriFromTemplate(t).toString -> "[]"
    )
  )

  it should "do not fail on dead flask" in {
    val d = Flask.gatherAssignedTargets(flasks = Seq(flask04))(fakeHttp).unsafePerformSync

    d.isEmpty should equal(true)
  }

  it should "aggregate data from multiple flasks" in {
    val d = Flask.gatherAssignedTargets(flasks = Seq(flask01, flask02, flask03))(fakeHttp).unsafePerformSync

    d.keys.toSet should equal(Set(flask01, flask02, flask03))
    d.lookup(flask01).map(_.size) should equal(Some(2))
    d.lookup(flask03).map(_.size) should equal(Some(0))
  }

  it should "partially aggregate data from multiple flasks if some are dead" in {
    //flask4 is not part of fakeHttp
    val d = Flask.gatherAssignedTargets(flasks = Seq(flask04, flask01, flask02))(fakeHttp).unsafePerformSync

    d.keys.toSet should equal(Set(flask01, flask02))
    d.lookup(flask01).map(_.size) should equal(Some(2))
  }
}
