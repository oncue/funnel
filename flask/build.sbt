
common.settings

common.revolver

common.fatjar

libraryDependencies += "oncue.knobs" %% "core" % V.knobs

fork in Test := true

mainClass in run := Some("funnel.flask.Main")
