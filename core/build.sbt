
import oncue.build._

OnCue.baseSettings

BuildMetadata.settings

ScalaCheck.settings

ScalaTest.settings

ContinuousIntegration.produceCoverageReport := false

Custom.compilation

Custom.resources

Custom.binaryCompatibility

scalacOptions += "-language:postfixOps"

libraryDependencies ++= Seq(
  "oncue.typelevel"   %% "shapeless-scalacheck" % "0.4.0" % "test",
  "org.scalaz.stream" %% "scalaz-stream"        % "0.7.3a",
  "com.twitter"       %% "algebird-core"        % "0.8.0",
  "org.fusesource"     % "sigar"                % "1.6.4" classifier("native") classifier("") exclude("log4j", "log4j"),
  "org.slf4j"          % "log4j-over-slf4j"     % "1.7.+", // SIGAR requires the log4j legacy API
  "oncue.svc.journal" %% "core"                 % "2.1.+",
   "org.spire-math"   %% "spire"                % "0.11.0"
)

addCompilerPlugin("org.brianmckenna" %% "wartremover" % "0.9")

addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.7.1")


