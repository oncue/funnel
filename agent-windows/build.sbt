
common.settings

common.revolver

common.unmanaged

libraryDependencies ++= Seq(
  "net.databinder"  %% "unfiltered-filter"       % V.unfiltered,
  "net.databinder"  %% "unfiltered-netty-server" % V.unfiltered,
  "oncue.knobs"     %% "core"                    % V.knobs,
  "io.netty"         % "netty-handler"           % V.netty,
  "io.netty"         % "netty-codec"             % V.netty,
  "com.github.cjmx" %% "cjmx"                    % V.cjmx exclude("org.scala-sbt","completion") exclude("com.google.code.gson","gson")
)

/* this is basically a hack so that the windows agent can be compiled against jeromq */
scalaSource in Compile := baseDirectory.value / ".." / ".." / "agent" / "src" / "main" / "scala"

assemblyMergeStrategy in assembly := {
  case "META-INF/io.netty.versions.properties" => MergeStrategy.first
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}

mainClass in run := Some("funnel.agent.Main")
