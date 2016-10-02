
organization := "com.gu"
name := "facebook-news-bot"
scalacOptions ++= Seq("-feature", "-deprecation", "-unchecked", "-target:jvm-1.8", "-Xfatal-warnings")
scalacOptions in doc in Compile := Nil

scalaVersion := "2.11.8"

val CirceVersion = "0.5.0-M2"

val akkaVersion = "2.4.10"

resolvers += Resolver.bintrayRepo("hseeberger", "maven")

enablePlugins(RiffRaffArtifact, JavaAppPackaging)

val CapiVersion = "10.5"

libraryDependencies ++= Seq(
  "com.google.code.findbugs" % "jsr305" % "2.0.3" % Compile,
  "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",
  "ch.qos.logback" %  "logback-classic" % "1.1.7",
  "com.gu" %% "configuration-magic-core" % "1.3.0",
  "com.gu" %% "content-api-client" % CapiVersion,
  "io.circe" %% "circe-core" % CirceVersion,
  "io.circe" %% "circe-generic" % CirceVersion,
  "io.circe" %% "circe-parser" % CirceVersion,
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-contrib" % akkaVersion,
  "com.typesafe.akka" %% "akka-http-experimental" % akkaVersion,
  "com.typesafe.akka" %% "akka-http-spray-json-experimental" % akkaVersion,
  "com.github.ben-manes.caffeine" % "caffeine" % "2.3.3",
  "de.heikoseeberger" %% "akka-http-circe" % "1.10.0",
  "com.amazonaws" % "aws-java-sdk-dynamodb" % "1.10.51",
  "com.amazonaws" % "aws-java-sdk-ec2" % "1.10.52",
  "com.gu" %% "scanamo" % "0.7.0",
  "com.typesafe.akka" %% "akka-http-testkit" % akkaVersion,
  "org.scalatest" %% "scalatest" % "3.0.0" % "test",
  "com.gu" % "content-api-models-json" % CapiVersion % Test, //this version needs to match the one used by content-api-client
  "org.mockito" % "mockito-core" % "1.9.5"
)

startDynamoDBLocal <<= startDynamoDBLocal.dependsOn(compile in Test)
test in Test <<= (test in Test).dependsOn(startDynamoDBLocal)
testOptions in Test <+= dynamoDBLocalTestCleanup

riffRaffPackageName := "facebook-news-bot"
riffRaffPackageType := (packageZipTarball in Universal).value
riffRaffUploadArtifactBucket := Option("riffraff-artifact")
riffRaffUploadManifestBucket := Option("riffraff-builds")
