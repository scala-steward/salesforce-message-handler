
name := "salesforce-message-handler"

organization := "com.gu"

description:= "handle outbound messages from salesforce to update zuora and indentity"

version := "1.0"

scalaVersion := "2.11.8"

scalacOptions ++= Seq(
  "-deprecation",
  "-encoding", "UTF-8",
  "-target:jvm-1.8",
  "-Ywarn-dead-code"
)

libraryDependencies ++= Seq(
  "com.amazonaws" % "aws-lambda-java-log4j" % "1.0.0",
  "com.amazonaws" % "aws-lambda-java-core" % "1.1.0",
  "com.amazonaws" % "aws-java-sdk-sqs" % "1.11.171",
  "org.slf4j" % "slf4j-simple" % "1.7.25",
  "com.typesafe.play" %% "play-json" % "2.4.6",
  "org.scala-lang.modules" % "scala-xml_2.11" % "1.0.6",
  "org.specs2" %% "specs2-core" % "3.9.4" % "test",
  "org.specs2" %% "specs2-matcher-extra" % "3.9.4" % "test",
  "org.specs2" % "specs2-mock_2.11" % "3.9.4" % "test",
  "org.hamcrest" % "hamcrest-all" % "1.1" % "test",
  "org.mockito" % "mockito-all" % "1.9.5" % "test"
)

enablePlugins(RiffRaffArtifact)

assemblyJarName := s"${name.value}.jar"
riffRaffPackageType := assembly.value
riffRaffUploadArtifactBucket := Option("riffraff-artifact")
riffRaffUploadManifestBucket := Option("riffraff-builds")
riffRaffManifestProjectName := "MemSub::Subscriptions::Salesforce Message Handler"
riffRaffArtifactResources += (file("cfn.yaml"), s"${name.value}-cfn/cfn.yaml")

CxfKeys.wsdls += Wsdl("sfOutboundMessages", (baseDirectory.value / "wsdl/salesforce-outbound-message.wsdl").getPath)
