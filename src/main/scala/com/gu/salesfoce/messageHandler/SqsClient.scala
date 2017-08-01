package com.gu.salesfoce.messageHandler

import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.{ AWSCredentialsProviderChain, EnvironmentVariableCredentialsProvider, InstanceProfileCredentialsProvider }
import com.amazonaws.regions.Regions.EU_WEST_1
import com.amazonaws.services.sqs.AmazonSQSClient
import com.amazonaws.services.sqs.model._
import play.api.libs.json.Json

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

trait QueueClient {
  def send(queueName: String, message: String)(implicit ec: ExecutionContext): Future[Try[SendMessageResult]]
}

object SqsClient extends QueueClient with Logging {

  lazy val CredentialsProvider = new AWSCredentialsProviderChain(
    new ProfileCredentialsProvider("membership"),
    new InstanceProfileCredentialsProvider(false),
    new EnvironmentVariableCredentialsProvider())

  private val sqsClient = AmazonSQSClient.builder
    .withCredentials(CredentialsProvider)
    .withRegion(EU_WEST_1)
    .build()

  override def send(queueName: String, message: String)(implicit ec: ExecutionContext): Future[Try[SendMessageResult]] = {
    val queueUrl = sqsClient.getQueueUrl(queueName).getQueueUrl
    val payload = Json.toJson(message).toString()

    def sendToQueue(msg: String): SendMessageResult = {
      logger.info(s"sending to queue $queueUrl")
      sqsClient.sendMessage(new SendMessageRequest(queueUrl, msg))
    }

    Future {
      Try(sendToQueue(payload))
    }
  }

}