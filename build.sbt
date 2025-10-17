name := """notilytics"""
organization := "com.notilytics"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava)

scalaVersion := "2.13.17"

libraryDependencies ++= Seq(
  guice,
  "com.typesafe.play" %% "play-json" % "2.10.0-RC7",
  "org.mockito" % "mockito-core" % "5.10.0" % Test
)
