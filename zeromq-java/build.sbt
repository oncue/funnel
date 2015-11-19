
common.settings

libraryDependencies ++= Seq(
  "org.zeromq"       % "jeromq"               % "0.3.4",
  "org.scodec"      %% "scodec-core"          % V.scodec,
  "oncue.ermine"    %% "ermine-parser"        % V.ermine
)

/* this is basically a hack so that the windows agent can be compiled against jeromq */
scalaSource in Compile := baseDirectory.value / ".." / "zeromq" / "src" / "main" / "scala"

scalaSource in Test := baseDirectory.value / ".." / "zeromq" / "src" / "main" / "test"

addCompilerPlugin("org.scalamacros" % "paradise" % "2.0.1" cross CrossVersion.full)
