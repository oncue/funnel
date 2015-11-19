
common.settings

// Custom.binaryCompatibility

// pure-java implementation:
// libraryDependencies += "org.zeromq" % "jeromq" % "0.3.4"
// native c++ implementation with jni:
libraryDependencies ++= Seq(
  "org.zeromq"      % "jzmq"                  % "3.1.0",
  "org.scodec"      %% "scodec-core"          % "1.7.1",
  "oncue.ermine"    %% "ermine-parser"        % V.ermine
)

addCompilerPlugin("org.scalamacros" % "paradise" % "2.0.1" cross CrossVersion.full)
