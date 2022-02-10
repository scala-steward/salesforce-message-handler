package com.gu.salesforce.messageHandler

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.sqs.model.SendMessageResult
import com.gu.salesforce.messageHandler.APIGatewayResponse._
import com.sforce.soap._2005._09.outbound._
import play.api.libs.json.{ JsValue, Json }

import java.io.{ ByteArrayInputStream, InputStream, OutputStream }
import javax.xml.bind.JAXBContext
import javax.xml.soap.MessageFactory
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{ Await, Future }
import scala.util.{ Failure, Try }

trait RealDependencies {
  val queueClient = SqsClient
}

trait MessageHandler extends Logging {
  def queueClient: QueueClient

  val queueName = s"salesforce-outbound-messages-${Config.stage}"

  def parseMessage(requestBody: String) = {
    val is = new ByteArrayInputStream(requestBody.getBytes)
    val messageFactory = MessageFactory.newInstance()
    val soapMessage = messageFactory.createMessage(null, is)
    val body = soapMessage.getSOAPBody
    val jc = JAXBContext.newInstance(classOf[Notifications])
    val unmarshaller = jc.createUnmarshaller()
    val je = unmarshaller.unmarshal(body.extractContentAsDocument(), classOf[Notifications])
    je.getValue()
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

  def processNotifications(notifications: List[ContactNotification], outputStream: OutputStream) = {
    val contactListStr = notifications.map(_.getSObject.getId).mkString(", ")
    logger.info(s"contacts found in salesforce xml: [$contactListStr]")
    val FutureResponses = notifications.map(sendToQueue)
    val future = Future.sequence(FutureResponses).map { responses =>
      val errors = responses collect { case Failure(error) => error }
      if (errors.nonEmpty) {
        errors.foreach(error => logger.error(s"error while trying to send message to queue", error))
        logger.info(s"lambda execution failed. Contacts in request: [$contactListStr]")
        outputForAPIGateway(outputStream, internalServerError)
      } else {
        logger.info(s"lambda execution successful. Enqueued contacts: [$contactListStr]")
        outputForAPIGateway(outputStream, okResponse)
      }
    }
    Await.ready(future, Duration.Inf)
  }

  def handleRequest(inputStream: InputStream, outputStream: OutputStream, context: Context): Unit = {

    logger.info(s"Salesforce message handler lambda ${Config.stage} is starting up...")
    logger.info(s"using config from ${Config.bucket}/${Config.key}")
    val inputEvent = Json.parse(inputStream)
    if (!credentialsAreValid(inputEvent)) {
      logger.info("Request could not be authenticated")
      outputForAPIGateway(outputStream, unauthorized)
    } else {
      logger.info("Authenticated request successfully...")
      val body = (inputEvent \ "body").as[String]
      val parsedMessage = parseMessage(body)
      if (parsedMessage.getOrganizationId.startsWith(Config.salesforceOrganizationId)) {
        processNotifications(asScalaBuffer(parsedMessage.getNotification).toList, outputStream)
      } else {
        logger.info("Unexpected salesforce organization id in xml message")
        outputForAPIGateway(outputStream, unauthorized)
      }
    }
  }
}

object Lambda extends MessageHandler with RealDependencies