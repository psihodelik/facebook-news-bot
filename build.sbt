name := "facebook-news-bot"

version := "1.0"

scalaVersion := "2.11.8"

val CirceVersion = "0.5.0-M2"

val akkaVersion = "2.4.10"

resolvers += Resolver.bintrayRepo("hseeberger", "maven")

libraryDependencies ++= Seq(
  "com.gu" %% "configuration-magic-core" % "1.2.0",
  "com.gu" %% "content-api-client" % "10.2",
  "io.circe" %% "circe-core" % CirceVersion,
  "io.circe" %% "circe-generic" % CirceVersion,
  "io.circe" %% "circe-parser" % CirceVersion,
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-http-experimental" % akkaVersion,
  "com.typesafe.akka" %% "akka-http-spray-json-experimental" % akkaVersion,
  "de.heikoseeberger" %% "akka-http-circe" % "1.10.0",
  "com.amazonaws" % "aws-java-sdk-dynamodb" % "1.10.20"
)
