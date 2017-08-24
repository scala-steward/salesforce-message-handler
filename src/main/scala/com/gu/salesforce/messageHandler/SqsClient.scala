package com.gu.salesforce.messageHandler

import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.{ AWSCredentialsProviderChain, EnvironmentVariableCredentialsProvider, InstanceProfileCredentialsProvider }
import com.amazonaws.regions.Regions.EU_WEST_1
import com.amazonaws.services.sqs.AmazonSQSClient
import com.amazonaws.services.sqs.model._

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

trait QueueClient {
  def send(queueName: String, message: String)(implicit ec: ExecutionContext): Future[Try[SendMessageResult]]
}

object SqsClient extends QueueClient with Logging {
  private val sqsClient = AmazonSQSClient.builder
    .withCredentials(Config.credentialsProvider)
    .withRegion(EU_WEST_1)
    .build()

  override def send(queueName: String, message: String)(implicit ec: ExecutionContext): Future[Try[SendMessageResult]] = {
    val queueUrl = sqsClient.getQueueUrl(queueName).getQueueUrl

    def sendToQueue(msg: String): SendMessageResult = {
      logger.info(s"sending message to queue $queueUrl: $msg")
      val sendMessageRequest = new SendMessageRequest(queueUrl, msg)
      sqsClient.sendMessage(sendMessageRequest)
    }
    Future {
      Try(sendToQueue(message))
    }
  }
}