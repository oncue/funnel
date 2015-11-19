
common.settings

fork in test := true

initialCommands in console := """
import funnel._, chemist._
import PlatformEvent._
import scalaz.concurrent.Task
import scalaz.stream.Process
"""

libraryDependencies ++= Seq(
  "oncue.knobs"             %% "core"                    % V.knobs,
  "net.databinder.dispatch" %% "dispatch-core"           % V.dispatch,
  "net.databinder"          %% "unfiltered-filter"       % V.unfiltered,
  "net.databinder"          %% "unfiltered-netty-server" % V.unfiltered,
  "com.google.guava"         % "guava"                   % "18.0",
  "com.google.code.findbugs" % "jsr305"                  % "1.3.+" // needed to provide class javax.annotation.Nullable
)
