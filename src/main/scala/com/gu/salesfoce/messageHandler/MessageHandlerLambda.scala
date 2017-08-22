package com.gu.salesfoce.messageHandler

import com.amazonaws.services.lambda.runtime.Context
import java.io.{ ByteArrayInputStream, InputStream, OutputStream }

import scala.concurrent.duration.Duration
import java.util.concurrent.Executors
import javax.xml.bind.JAXBContext
import javax.xml.soap.MessageFactory

import com.amazonaws.services.sqs.model.SendMessageResult

import scala.collection.JavaConversions._
import com.gu.salesfoce.messageHandler.ResponseModels.{ ApiResponse, Headers }
import com.sforce.soap._2005._09.outbound._
import play.api.libs.json.{ JsValue, Json }
import com.gu.salesfoce.messageHandler.APIGatewayResponse._

import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

trait RealDependencies {
  val queueClient = SqsClient
}

trait MessageHandler extends Logging {
  def queueClient: QueueClient

  val queueName = s"salesforce-outbound-messages-${Config.stage}"
  val ThreadCount = 10
  implicit val executionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(ThreadCount))

  val okXml =
    """<?xml version="1.0" encoding="UTF-8"?>
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
    logger.info(requestBody) //TODO DELETE THIS LATER!!
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

  def credentialsAreValid(inputEvent: JsValue): Boolean = {

    val maybeApiClientId = (inputEvent \ "queryStringParameters" \ "apiClientId").asOpt[String]
    val maybeApiClientToken = (inputEvent \ "queryStringParameters" \ "apiToken").asOpt[String]
    val maybeCredentials = (maybeApiClientId, maybeApiClientToken)
    maybeCredentials match {
      case (Some(apiClientId), Some(apiToken)) => {
        (apiClientId == Config.apiClientId && apiToken == Config.apiToken)
      }
      case _ => {
        logger.info(s"Could not find credentials in request")
        false
      }
    }
  }

  def sendToQueue(notification: ContactNotification): Future[Try[SendMessageResult]] = {
    val queueMessage = QueueMessage(notification.getSObject.getId)
    val queueMessageString = Json.prettyPrint(Json.toJson(queueMessage))
    queueClient.send(queueName, queueMessageString)
  }

  def handleRequest(inputStream: InputStream, outputStream: OutputStream, context: Context): Unit = {

    logger.info(s"Salesforce message handler lambda ${Config.stage} is starting up...")
    val inputEvent = Json.parse(inputStream)
    if (!credentialsAreValid(inputEvent)) {
      logger.info("Request from Zuora could not be authenticated")
      outputForAPIGateway(outputStream, unauthorized)
    } else {
      logger.info("Authenticated request successfully...")
      val body = (inputEvent \ "body").as[String]
      val notifications = parseMessage(body)
      val FutureResponses = notifications.map(sendToQueue)
      val future = Future.sequence(FutureResponses).map { responses =>
        val errors = responses collect { case Failure(error) => error }
        if (errors.nonEmpty) {
          errors.foreach(error =>
            logger.error(s"error while trying to send message to queue", error))
            outputForAPIGateway(outputStream, internalServerError)
        } else {
          outputForAPIGateway(outputStream, okResponse)
        }
      }
      Await.ready(future, Duration.Inf)
    }
  }

}

object Lambda extends MessageHandler with RealDependencies