
common.settings

common.revolver

common.unmanaged

common.fatjar

libraryDependencies ++= Seq(
  "net.databinder"    %% "unfiltered-filter"       % V.unfiltered,
  "net.databinder"    %% "unfiltered-netty-server" % V.unfiltered,
  "oncue.knobs"       %% "core"                    % V.knobs,
  "io.netty"           % "netty-handler"           % V.netty,
  "io.netty"           % "netty-codec"             % V.netty,
  "com.github.cjmx"   %% "cjmx"                    % V.cjmx exclude("org.scala-sbt","completion") exclude("com.google.code.gson","gson"),
  "org.apache.curator" % "curator-test"            % "2.9.0" % "test"
)

mainClass in run := Some("funnel.agent.Main")
