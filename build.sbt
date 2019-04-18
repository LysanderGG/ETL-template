ThisBuild / scalaVersion := "2.12.6"

lazy val circeVersion = "0.9.3"

lazy val akkaStreamContrib = ProjectRef(uri("https://github.com/kkvesper/akka-stream-contrib.git#improve_accumulate_while_unchanged"), "contrib")

lazy val etl = (project in file("."))
  .enablePlugins(JavaAppPackaging)
  .dependsOn(akkaStreamContrib)
  .settings(
    name := "General ETL",
    libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.3",
    libraryDependencies += "com.typesafe.akka" %% "akka-slf4j" % "2.5.12",
    libraryDependencies += "com.typesafe.akka" %% "akka-stream" % "2.5.12",
    libraryDependencies += "com.typesafe.akka" %% "akka-stream-kafka" % "1.0-M1",
    libraryDependencies += "com.typesafe.akka" %% "akka-http" % "10.1.5",
    libraryDependencies += "io.sentry" % "sentry-logback" % "1.7.5",
    libraryDependencies += "com.workday" %% "prometheus-akka" % "0.8.5",
    libraryDependencies += "org.flywaydb" % "flyway-core" % "5.2.1",
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core",
      "io.circe" %% "circe-generic",
      "io.circe" %% "circe-generic-extras",
      "io.circe" %% "circe-parser",
      "io.circe" %% "circe-java8",
    ).map(_ % circeVersion),
    libraryDependencies ++= Seq(
      "org.scalikejdbc" %% "scalikejdbc" % "3.2.2",
      "org.scalikejdbc" %% "scalikejdbc-config" % "3.2.2",
    ),
    libraryDependencies ++= Seq(
      "org.scalatest" % "scalatest_2.12" % "3.0.5",
      "com.typesafe.akka" %% "akka-stream-testkit" % "2.5.16",
      "net.manub" %% "scalatest-embedded-kafka" % "2.0.0",
    ).map(_ % "test")
  )

addCompilerPlugin(
  ("org.scalamacros" % "paradise" % "2.1.1").cross(CrossVersion.full)
)
