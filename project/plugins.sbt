
resolvers += Resolver.url(
  "bintray-sbt-plugin-releases",
    url("http://dl.bintray.com/content/sbt/sbt-plugin-releases"))(
        Resolver.ivyStylePatterns)

resolvers += Resolver.url(
  "tpolecat-sbt-plugin-releases",
    url("http://dl.bintray.com/content/tpolecat/sbt-plugin-releases"))(
        Resolver.ivyStylePatterns)

addSbtPlugin("me.lessis"         % "bintray-sbt"     % "0.3.0")
addSbtPlugin("com.github.gseitz" % "sbt-release"     % "1.0.0")
addSbtPlugin("com.typesafe.sbt"  % "sbt-site"        % "0.8.1")
addSbtPlugin("com.typesafe.sbt"  % "sbt-ghpages"     % "0.5.3")
addSbtPlugin("org.tpolecat"      % "tut-plugin"      % "0.3.2")
addSbtPlugin("com.timushev.sbt"  % "sbt-updates"     % "0.1.8")
addSbtPlugin("com.jsuereth"      % "sbt-pgp"         % "1.0.0")
addSbtPlugin("com.eed3si9n"      % "sbt-unidoc"      % "0.3.3")
// has a transitive dependency on sbt-assembly 0.13.0
addSbtPlugin("com.typesafe.sbt"  % "sbt-multi-jvm"   % "0.3.11")
addSbtPlugin("io.spray"          % "sbt-revolver"    % "0.7.2")
addSbtPlugin("com.typesafe"      % "sbt-mima-plugin" % "0.1.7")
addSbtPlugin("com.eed3si9n"      % "sbt-buildinfo"   % "0.5.0")
