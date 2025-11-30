import scala.collection.immutable.Seq

name := """notilytics"""
organization := "com.notilytics"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava)

scalaVersion := "2.13.17"

libraryDependencies ++= Seq(
  guice,
  "com.typesafe.play" %% "play-json" % "2.10.0-RC7",
  "org.mockito" % "mockito-core" % "5.11.0" % Test,
  "org.mockito" % "mockito-inline" % "5.2.0" % Test,
  "org.junit.jupiter" % "junit-jupiter-api" % "5.10.0" % Test,
  "org.junit.jupiter" % "junit-jupiter-engine" % "5.10.0" % Test,
  "org.apache.pekko" %% "pekko-actor-testkit-typed" % "1.1.2" % Test,
    javaWs,
  ehcache)

libraryDependencies ++= Seq(
  "org.apache.pekko" %% "pekko-actor" % "1.1.2",
  "org.apache.pekko" %% "pekko-actor-typed" % "1.1.2",
  "org.apache.pekko" %% "pekko-actor-testkit-typed" % "1.1.2" % Test,
  "org.apache.pekko" %% "pekko-slf4j" % "1.1.2",
  "org.apache.pekko" %% "pekko-testkit" % "1.1.2" % Test,
  "org.apache.pekko" %% "pekko-protobuf-v3" % "1.1.2",
  "org.apache.pekko" %% "pekko-serialization-jackson" % "1.1.2",
  "org.apache.pekko" %% "pekko-stream" % "1.1.2"
)



