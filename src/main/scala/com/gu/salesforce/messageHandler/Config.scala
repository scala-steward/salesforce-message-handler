package com.gu.salesforce.messageHandler

import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.{ AWSCredentialsProviderChain, DefaultAWSCredentialsProviderChain }
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.typesafe.config.ConfigFactory

import scala.io.Source

object Config {
  case class Env(app: String, stack: String, stage: String) {
    override def toString: String = s"App: $app, Stack: $stack, Stage: $stage\n"
  }

  object Env {
    def apply(): Env = Env(
      Option(System.getenv("App")).getOrElse("salesforce-message-handler"),
      Option(System.getenv("Stack")).getOrElse("membership"),
      Option(System.getenv("Stage")).getOrElse("CODE"))
  }

  val stage = Env().stage.toUpperCase

  val credentialsProvider = new AWSCredentialsProviderChain(
    new ProfileCredentialsProvider("membership"),
    new DefaultAWSCredentialsProviderChain())

  val s3Client = AmazonS3ClientBuilder
    .standard()
    .withCredentials(credentialsProvider)
    .build()

  val configData = {
    val key = s"/$stage/salesforce-message-handler.private.conf"
    val s3Object = s3Client.getObject("membership-private", s"$stage/salesforce-message-handler.private.conf")
    val source = Source.fromInputStream(s3Object.getObjectContent)
    try {
      val conf = source.mkString
      ConfigFactory.parseString(conf)
    } finally {
      source.close()
    }
  }

  val apiClientId = configData.getString("apiClientId")
  val apiToken = configData.getString("apiToken")
  val salesforceOrganizationId = configData.getString("salesforceOrganizationId")
}
