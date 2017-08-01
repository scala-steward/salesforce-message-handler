package com.gu.salesfoce.messageHandler

import com.amazonaws.services.lambda.runtime.Context
import java.io.{ InputStream, OutputStream }
import scala.concurrent.duration.Duration
import java.util.concurrent.Executors

import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.{ AWSCredentialsProviderChain, InstanceProfileCredentialsProvider }
import com.amazonaws.regions.Regions.EU_WEST_1
import com.amazonaws.services.sqs.AmazonSQSClient
import com.gu.salesfoce.messageHandler.ResponseModels.{ ApiResponse, Headers }
import play.api.libs.json.Json

import scala.concurrent.{ Await, ExecutionContext }
import scala.util.{ Failure, Success }

case class Env(app: String, stack: String, stage: String) {
  override def toString: String = s"App: $app, Stack: $stack, Stage: $stage\n"
}

object Env {
  def apply(): Env = Env(
    Option(System.getenv("App")).getOrElse("DEV"),
    Option(System.getenv("Stack")).getOrElse("DEV"),
    Option(System.getenv("Stage")).getOrElse("DEV"))
}

object Lambda extends Logging {
  val ThreadCount = 10
  implicit val executionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(ThreadCount))

  val okXml =
    """
      |<?xml version="1.0" encoding="UTF-8"?>
      |<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:xsd="http://www.w3.org/2001/XMLSchema" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
      | <soapenv:Body>
      |  <element name="notificationsResponse">
      |    <complexType>
      |        <sequence>
      |            <element name="Ack" type="xsd:boolean" />
      |        </sequence>
      |    </complexType>
      |</element>
      | </soapenv:Body>
      |</soapenv:Envelope
    """.stripMargin
  val okResponse = ApiResponse("200", Headers(), okXml)

  def handleRequest(inputStream: InputStream, outputStream: OutputStream, context: Context): Unit = {
    val stage = Env().stage.toUpperCase
    logger.info(s"Salesforce message handler lambda ${stage} is starting up...")
    val inputEvent = Json.parse(inputStream)
    val body = (inputEvent \ "body").as[String]

    val response = SqsClient.send(s"salesforce-outbound-messages-zuora-${stage}.fifo", body).map {
      case Success(r) =>
        logger.info("successfully sent to queue")
        APIGatewayResponse.outputForAPIGateway(outputStream, okResponse)
      case Failure(ex) =>
        logger.error("could not send message to queue", ex)
        APIGatewayResponse.outputForAPIGateway(outputStream, ApiResponse("500", Headers(), "server error")) //see if we need anything better than this!

    }
    Await.ready(response, Duration.Inf)

  }

}
