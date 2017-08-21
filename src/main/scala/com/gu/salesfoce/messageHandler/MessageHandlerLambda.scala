package com.gu.salesfoce.messageHandler

import com.amazonaws.services.lambda.runtime.Context
import java.io.{ ByteArrayInputStream, InputStream, OutputStream }

import scala.concurrent.duration.Duration
import java.util.concurrent.Executors
import javax.xml.bind.JAXBContext
import javax.xml.soap.MessageFactory

import scala.collection.JavaConversions._
import com.gu.salesfoce.messageHandler.ResponseModels.{ ApiResponse, Headers }
import com.sforce.soap._2005._09.outbound._
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

trait RealDependencies {
  val queueClient = SqsClient
}

trait MessageHandler extends Logging {
  def queueClient: QueueClient

  val ThreadCount = 10
  implicit val executionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(ThreadCount))

  val okXml =
    """
      |<?xml version="1.0" encoding="UTF-8"?>
      |<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
      |	<soapenv:Body>
      |		<notificationsResponse xmlns="http://soap.sforce.com/2005/09/outbound">
      |			<Ack>true</Ack>
      |		</notificationsResponse>
      |	</soapenv:Body>
      |</soapenv:Envelope>
    """.stripMargin

  val okResponse = ApiResponse("200", Headers(), okXml)

  def parseMessage(requestBody: String): List[ContactNotification] = {
    logger.info(s"trying to parse ")
    val is = new ByteArrayInputStream(requestBody.getBytes)
    val messageFactory = MessageFactory.newInstance()
    val soapMessage = messageFactory.createMessage(null, is)
    val body = soapMessage.getSOAPBody
    val jc = JAXBContext.newInstance(classOf[Notifications])
    val unmarshaller = jc.createUnmarshaller()
    val je = unmarshaller.unmarshal(body.extractContentAsDocument(), classOf[Notifications])
    val notifications = je.getValue()
    notifications.getNotification.toList
  }

  case class QueueMessage(contactId: String)

  implicit val messageFormat = Json.format[QueueMessage]

  def handleRequest(inputStream: InputStream, outputStream: OutputStream, context: Context): Unit = {
    val stage = Env().stage.toUpperCase
    logger.info(s"Salesforce message handler lambda ${stage} is starting up...")
    val queueName = s"salesforce-outbound-messages-${stage}"
    val inputEvent = Json.parse(inputStream)
    val body = (inputEvent \ "body").as[String]
    val notifications = parseMessage(body)

    notifications.foreach { notification =>
      val queueMessage = QueueMessage(notification.getId)
      val queueMessageString = Json.prettyPrint(Json.toJson(queueMessage))

      logger.info(s"sending message to queue $queueName")
      val response = queueClient.send(queueName, queueMessageString).map {
        case Success(r) =>
          logger.info("successfully sent to queue")
          APIGatewayResponse.outputForAPIGateway(outputStream, okResponse)
        case Failure(ex) =>
          logger.error("could not send message to queue", ex)
          APIGatewayResponse.outputForAPIGateway(outputStream, ApiResponse("500", Headers(), "server error")) //see if we need anything better than this!
      }
      Await.ready(response, Duration.Inf) //TODO SEE WHAT IS THE CORRECT WAY OF WAITING FOR THE FUTURE
    }

  }

}

object Lambda extends MessageHandler with RealDependencies