
Revolver.settings

common.settings

common.revolver

common.fatjar

libraryDependencies += "io.verizon.knobs" %% "core" % V.knobs

// fork in Test := true

mainClass in run := Some("funnel.flask.Main")
