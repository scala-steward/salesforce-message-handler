package com.gu.salesfoce.messageHandler

import com.amazonaws.auth.{ AWSCredentialsProviderChain, InstanceProfileCredentialsProvider, SystemPropertiesCredentialsProvider }
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.typesafe.config.ConfigFactory

import scala.io.{ BufferedSource, Source }

////todo load token here!!
object Config {

  case class Env(app: String, stack: String, stage: String) {
    override def toString: String = s"App: $app, Stack: $stack, Stage: $stage\n"
  }

  object Env {
    def apply(): Env = Env(
      Option(System.getenv("App")).getOrElse("DEV"),
      Option(System.getenv("Stack")).getOrElse("DEV"),
      Option(System.getenv("Stage")).getOrElse("DEV"))
  }

  val credentialsProvider = new AWSCredentialsProviderChain(
    InstanceProfileCredentialsProvider.getInstance(),
    new ProfileCredentialsProvider("membership"))

  val s3Client = AmazonS3ClientBuilder
    .standard()
    .withCredentials(credentialsProvider)
    .build()

  val s3Object = s3Client.getObject("membership-private", s"/$stage/salesforce-message-handler.private.conf")

  val configData = {
    val source = Source.fromInputStream(s3Object.getObjectContent)
    try {
      val conf = source.mkString
      ConfigFactory.parseString(conf)
    } finally {
      source.close()
    }
  }

  val stage = Env().stage.toUpperCase

  val apiClientId = configData.getString("apiClientId")
  val apiToken = configData.getString("apiToken")
}
