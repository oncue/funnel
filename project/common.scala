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
import sbt._, Keys._
import sbtrelease._
import sbtrelease.ReleasePlugin.autoImport._
import sbtrelease.ReleaseStateTransformations._
import sbtrelease.Utilities._
import com.typesafe.sbt.pgp.PgpKeys._
import bintray.BintrayKeys._
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm
import spray.revolver.RevolverPlugin._
import sbtassembly.{MergeStrategy,AssemblyKeys}, AssemblyKeys._

object common {

  def settings =
    prompt.settings ++
    artifactRepositories ++
    compilation ++
    bintraySettings ++
    releaseSettings ++
    publishingSettings ++
    testSettings

  val scalaTestVersion  = SettingKey[String]("scalatest-version")
  val scalaCheckVersion = SettingKey[String]("scalacheck-version")

  val toolsJar = {
    val javaHome = Path(Path.fileProperty("java.home").asFile.getParent)
    val toolsJar = Option(javaHome / "lib" / "tools.jar").filter { _.exists }
    if (toolsJar.isEmpty && !appleJdk6OrPrior) sys.error("tools.jar not in $JAVA_HOME/lib")
    toolsJar.toSeq
  }

  def compilation = Seq(
    scalacOptions in Compile ++= Seq(
      "-deprecation",
      "-encoding", "UTF-8",
      "-feature",
      "-language:higherKinds",
      "-language:implicitConversions",
      "-unchecked",
      "-Xfatal-warnings",
      "-Yno-adapted-args",
      "-Ywarn-dead-code",
      "-Xfuture"
      // "-Ywarn-numeric-widen",
      // "-Ywarn-value-discard",
    )
  )

  def unmanaged = Seq(
    unmanagedClasspath in Compile ++= toolsJar,
    unmanagedClasspath in Test ++= toolsJar
  )

  def testSettings = Seq(
    scalaTestVersion     := "2.2.5",
    scalaCheckVersion    := "1.12.3",
    libraryDependencies ++= Seq(
      "org.scalatest"  %% "scalatest"  % scalaTestVersion.value  % "test",
      "org.scalacheck" %% "scalacheck" % scalaCheckVersion.value % "test"
    ),
    compile in MultiJvm <<= (compile in MultiJvm) triggeredBy (compile in Test),
    executeTests in Test <<= (executeTests in Test, executeTests in MultiJvm) map {
      case (testResults, multiNodeResults)  =>
        val overall =
          if (testResults.overall.id < multiNodeResults.overall.id)
            multiNodeResults.overall
          else
            testResults.overall
        Tests.Output(overall,
          testResults.events ++ multiNodeResults.events,
          testResults.summaries ++ multiNodeResults.summaries)
    },
    unmanagedResourceDirectories in Test <+= baseDirectory(_ / ".." / "etc" / "classpath" / "test"),
    unmanagedResourceDirectories in MultiJvm <+= baseDirectory(_ / ".." / "etc" / "classpath" / "test")
  )

  /**
   * when using these settings, you need to make sure your module defines
   * `mainClass in run := Some("foo.bar.Main")` otherwise things will not
   * work as intended.
   */
  def revolver = Seq(
    javaOptions in Revolver.reStart += s"-Dlogback.configurationFile=${baseDirectory.value}/../etc/classpath/logback.xml",
    Revolver.reStartArgs :=
      (baseDirectory.value / ".." / "etc" / "development" / name.value / s"${name.value}.dev.cfg").getCanonicalPath :: Nil,
    mainClass in Revolver.reStart := (mainClass in run).value
  )

  def artifactRepositories = Seq(
    resolvers ++= Resolver.jcenterRepo ::
                  Resolver.bintrayRepo("oncue", "releases") :: Nil
  )

  def fatjar = Seq(
    assemblyMergeStrategy in assembly := {
      case "META-INF/io.netty.versions.properties" => MergeStrategy.first
      case x =>
        val oldStrategy = (assemblyMergeStrategy in assembly).value
        oldStrategy(x)
    },
    artifact in (Compile, assembly) := {
      val art = (artifact in (Compile, assembly)).value
      art.copy(`classifier` = Some("assembly"))
    },
    mainClass in assembly := (mainClass in run).value
  ) ++ addArtifact(artifact in (Compile, assembly), assembly)

  def ignore = Seq(
    publish := (),
    publishSigned := (),
    publishLocal := (),
    publishLocalSigned := (),
    publishArtifact in Test := false,
    publishArtifact in Compile := false
  )

  def bintraySettings = Seq(
    bintrayPackageLabels := Seq("monitoring", "functional programming", "scala", "reasonable"),
    bintrayOrganization := Some("oncue"),
    bintrayRepository := "releases",
    bintrayPackage := "funnel"
  )

  def releaseSettings = Seq(
    releaseCrossBuild := true,
    releasePublishArtifactsAction := publishSigned.value,
    releaseProcess := Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      runTest,
      setReleaseVersion,
      commitReleaseVersion,
      tagRelease,
      publishArtifacts,
      setNextVersion,
      commitNextVersion,
      pushChanges
    )
  )

  def publishingSettings = Seq(
    pomExtra := (
      <developers>
        <developer>
          <id>timperrett</id>
          <name>Timothy Perrett</name>
          <url>http://github.com/timperrett</url>
        </developer>
        <developer>
          <id>runarorama</id>
          <name>Runar Bjarnason</name>
          <url>http://github.com/runarorama</url>
        </developer>
        <developer>
          <id>stew</id>
          <name>Stew O'Connor</name>
          <url>http://github.com/stew</url>
        </developer>
        <developer>
          <id>pchiusano</id>
          <name>Paul Chiusano</name>
          <url>http://github.com/pchiusano</url>
        </developer>
        <developer>
          <id>ceedubs</id>
          <name>Cody Allen</name>
          <url>http://github.com/ceedubs</url>
        </developer>
        <developer>
          <id>danbills</id>
          <name>Dan Billings</name>
          <url>http://github.com/danbills</url>
        </developer>
        <developer>
          <id>jpcarey</id>
          <name>Jared Carey</name>
          <url>http://github.com/jpcarey</url>
        </developer>
        <developer>
          <id>vidhyaarvind</id>
          <name>Vidhya Arvind</name>
          <url>http://github.com/vidhyaarvind</url>
        </developer>
      </developers>),
    publishMavenStyle := true,
    useGpg := true,
    licenses := Seq("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0.html")),
    homepage := Some(url("http://oncue.github.io/funnel/")),
    scmInfo := Some(ScmInfo(url("https://github.com/oncue/funnel"),
                                "git@github.com:oncue/funnel.git")),
    pomIncludeRepository := { _ => false },
    publishArtifact in Test := false
  )

  def appleJdk6OrPrior: Boolean = {
    (sys.props("java.vendor") contains "Apple") && {
      val JavaVersion = """^(\d+)\.(\d+)\..*$""".r
      val JavaVersion(major, minor) = sys.props("java.version")
      major.toInt == 1 && minor.toInt < 7
    }
  }
}
