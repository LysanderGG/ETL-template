addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.3.6")
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.5.1")

resolvers += Resolver.url(
  "bintray-sbt-plugins",
  url("https://dl.bintray.com/sbt/sbt-plugin-releases/"),
)(Resolver.ivyStylePatterns)
