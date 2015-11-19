
import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm

common.ignore

prompt.settings

organization in Global  := "oncue.funnel"

scalaVersion in Global  := "2.10.5"

parallelExecution in Global := false

lazy val funnel = project.in(file(".")).aggregate(
  core,
  http,
  elastic,
  nginx,
  integration,
  zeromq,
  agent,
  `zeromq-java`,
  flask,
  chemist,
  `chemist-aws`,
  `chemist-static`,
  `agent-windows`
)

lazy val agent = project.dependsOn(zeromq % "test->test;compile->compile", http, nginx).configs(MultiJvm)

lazy val `agent-windows` = project.dependsOn(`zeromq-java`, http, nginx).configs(MultiJvm)

lazy val chemist = project.dependsOn(core, http, zeromq)

lazy val `chemist-aws` = project.dependsOn(chemist % "test->test;compile->compile")

lazy val `chemist-static` = project.dependsOn(chemist % "test->test;compile->compile")

lazy val core = project.enablePlugins(BuildInfoPlugin)

lazy val docs = project.dependsOn(core)

lazy val elastic = project.dependsOn(core, http)

lazy val flask = project.dependsOn(elastic, zeromq % "test->test;compile->compile")

lazy val http = project.dependsOn(core)

lazy val integration = project.dependsOn(flask, chemist % "test->test;compile->compile").configs(MultiJvm)

lazy val nginx = project.dependsOn(core)

lazy val zeromq = project.dependsOn(core, http).configs(MultiJvm) // http? this is for http.JSON._, which should be fixed probably

lazy val `zeromq-java` = project.dependsOn(http).configs(MultiJvm)
