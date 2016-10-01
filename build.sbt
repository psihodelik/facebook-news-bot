name := "facebook-news-bot"

version := "1.0"

scalaVersion := "2.11.8"

val CirceVersion = "0.5.0-M2"

val akkaVersion = "2.4.10"

resolvers += Resolver.bintrayRepo("hseeberger", "maven")

libraryDependencies ++= Seq(
  "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",
  "ch.qos.logback" %  "logback-classic" % "1.1.7",
  "com.gu" %% "configuration-magic-core" % "1.2.0",
  "com.gu" %% "content-api-client" % "10.2",
  "io.circe" %% "circe-core" % CirceVersion,
  "io.circe" %% "circe-generic" % CirceVersion,
  "io.circe" %% "circe-parser" % CirceVersion,
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-http-experimental" % akkaVersion,
  "de.heikoseeberger" %% "akka-http-circe" % "1.10.0",
  "com.amazonaws" % "aws-java-sdk-dynamodb" % "1.10.20",
  "com.gu" %% "scanamo" % "0.7.0",
  "com.typesafe.akka" %% "akka-http-testkit" % akkaVersion,
  "org.scalatest" %% "scalatest" % "3.0.0" % "test"
)

startDynamoDBLocal <<= startDynamoDBLocal.dependsOn(compile in Test)
test in Test <<= (test in Test).dependsOn(startDynamoDBLocal)
testOptions in Test <+= dynamoDBLocalTestCleanup
