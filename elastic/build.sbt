
common.settings

fork in (run in Test) := true

libraryDependencies ++= Seq(
  "net.databinder.dispatch" %% "dispatch-core" % V.dispatch,
  "io.verizon.knobs"        %% "core"          % V.knobs
)

scalacOptions += "-language:postfixOps"
