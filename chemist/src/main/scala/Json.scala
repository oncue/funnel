package funnel
package chemist

object JSON {
  import argonaut._, Argonaut._
  import javax.xml.bind.DatatypeConverter // hacky, but saves the extra dependencies
  import java.net.URI
  import java.text.SimpleDateFormat
  import java.util.Date

  implicit class AsDate(in: String){
    def asDate: java.util.Date =
      javax.xml.bind.DatatypeConverter.parseDateTime(in).getTime
  }

  implicit val FlaskIdToJson: EncodeJson[FlaskID] =
    implicitly[EncodeJson[String]].contramap(_.value)

  implicit val UriToJson: EncodeJson[URI] =
    implicitly[EncodeJson[String]].contramap(_.toString)

  implicit val DateToJson: EncodeJson[Date] =
    implicitly[EncodeJson[String]].contramap {
      new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ",java.util.Locale.US).format(_)
    }

  implicit def TargetEncodeJson: EncodeJson[Target] =
    EncodeJson((t: Target) =>
      ("cluster" := t.cluster) ->:
      ("uri"     := t.uri) ->: jEmptyObject)

  ////////////////////// Chemist messages //////////////////////

  /**
   * {
   *   "shard": "instance-f1",
   *   "targets": [
   *     {
   *       "cluster": "testing",
   *       "uris": [
   *         "http://...:5775/stream/sliding",
   *         "http://...:5775/stream/sliding"
   *       ]
   *     }
   *   ]
   * }
   */
  def encodeClusterPairs[A : EncodeJson]: EncodeJson[(A, Map[ClusterName, List[URI]])] =
    EncodeJson((m: (A, Map[ClusterName, List[URI]])) =>
      ("shard"   := m._1) ->:
      ("targets" := m._2.toList) ->: jEmptyObject
    )

  implicit val SnapshotWithFlaskToJson: EncodeJson[(FlaskID, Map[ClusterName, List[URI]])] =
    encodeClusterPairs[FlaskID]

  /**
   * {
   *   "id": "flask1",
   *   "location": ...
   * }
   */
  implicit val FlaskToJson: EncodeJson[Flask] =
    EncodeJson((f: Flask) =>
      ("id"       := f.id)       ->:
      ("location" := f.location) ->: jEmptyObject)

  implicit val LocationToJson: EncodeJson[Location] =
    EncodeJson { l =>
      ("host" := l.host) ->:
      ("port" := l.port) ->:
      ("datacenter" := l.datacenter) ->:
      ("protocol" := l.protocol.toString) ->:
      jEmptyObject
    }

  ////////////////////// flask messages //////////////////////

  /**
   *[
   *  {
   *    "cluster": "imqa-maestro-1-0-261-QmUo7Js",
   *    "uris": [
   *      "http://ec2-23-20-119-134.compute-1.amazonaws.com:5775/stream/sliding",
   *      "http://ec2-23-20-119-134.compute-1.amazonaws.com:5775/stream/uptime",
   *      "http://ec2-54-81-136-185.compute-1.amazonaws.com:5775/stream/sliding",
   *      "http://ec2-54-81-136-185.compute-1.amazonaws.com:5775/stream/uptime"
   *    ]
   *  }
   *]
   */
  implicit val ClustersToJSON: EncodeJson[(String, List[URI])] =
    EncodeJson((t: (String, List[URI])) =>
      ("cluster" := t._1) ->:
      ("uris"   := t._2 ) ->: jEmptyObject
    )

  ////////////////////// lifecycle events //////////////////////

  def encodeNewTarget(t: Target, time: Date): Json = {
    ("type" := "NewTarget") ->:
    ("cluster" := t.cluster) ->:
    ("uri" := t.uri) ->:
    ("time" := time) ->:
    jEmptyObject
  }

  def encodeNewFlask(f: Flask, time: Date): Json = {
    ("type" := "NewFlask") ->:
    ("flask" := f.id) ->:
    ("location" := f.location) ->:
    ("time" := time) ->:
    jEmptyObject
  }

  def encodeTerminatedTarget(u: URI, time: Date): Json = {
    ("type" := "TerminatedTarget") ->:
    ("uri" := u) ->:
    ("time" := time) ->:
    jEmptyObject
  }

  def encodeTerminatedFlask(f: FlaskID, time: Date): Json = {
    ("type" := "TerminatedFlask") ->:
    ("flask" := f.value) ->:
    ("time" := time) ->:
    jEmptyObject
  }

  def encodeMonitored(f: FlaskID, u: URI, time: Date): Json = {
    ("type" := "Monitored") ->:
    ("flask" := f.value) ->:
    ("uri" := u) ->:
    ("time" := time) ->:
    jEmptyObject
  }

  def encodeProblem(f: FlaskID, u: URI, msg: String, time: Date): Json = {
    ("type" := "Problem") ->:
    ("flask" := f.value) ->:
    ("uri" := u) ->:
    ("message" := msg) ->:
    ("time" := time) ->:
    jEmptyObject
  }

  def encodeUnmonitored(f: FlaskID, u: URI, time: Date): Json = {
    ("type" := "Unmonitored") ->:
    ("flask" := f.value) ->:
    ("uri" := u) ->:
    ("time" := time) ->:
    jEmptyObject
  }

  def encodeAssigned(f: FlaskID, t: Target, time: Date): Json = {
    ("type" := "Assigned") ->:
    ("flask" := f.value) ->:
    ("cluster" := t.cluster) ->:
    ("uri" := t.uri) ->:
    ("time" := time) ->:
    jEmptyObject
  }

  implicit val platformEventToJson: EncodeJson[PlatformEvent] =
    EncodeJson {
      case e @ PlatformEvent.NewTarget(t) => encodeNewTarget(t, e.time)
      case e @ PlatformEvent.NewFlask(f) => encodeNewFlask(f, e.time)
      case e @ PlatformEvent.TerminatedTarget(u) => encodeTerminatedTarget(u, e.time)
      case e @ PlatformEvent.TerminatedFlask(f) => encodeTerminatedFlask(f, e.time)
      case e @ PlatformEvent.Unmonitored(f, u) => encodeUnmonitored(f, u, e.time)
      case e @ PlatformEvent.NoOp => ("type" := "NoOp") ->: ("time" := e.time) ->: jEmptyObject
    }
}
