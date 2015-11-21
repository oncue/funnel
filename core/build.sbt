
import sbtbuildinfo.BuildInfoPlugin, BuildInfoPlugin.BuildInfoKey

common.settings

metadata.settings

// Custom.binaryCompatibility

scalacOptions += "-language:postfixOps"

libraryDependencies ++= Seq(
  "org.scalaz.stream" %% "scalaz-stream"        % "0.7.3a",
  "com.twitter"       %% "algebird-core"        % "0.8.0",
  "org.fusesource"     % "sigar"                % "1.6.4" classifier("native") classifier("") exclude("log4j", "log4j"),
  "org.slf4j"          % "log4j-over-slf4j"     % "1.7.+", // SIGAR requires the log4j legacy API
  "oncue.journal"     %% "core"                 % "2.2.1"
)

addCompilerPlugin("org.brianmckenna" %% "wartremover" % "0.14")

addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.7.1")
