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
import play.api.libs.json.{ JsValue, Json }

import scala.concurrent.{ Await, ExecutionContext }
import scala.util.{ Failure, Success }

trait RealDependencies {
  val queueClient = SqsClient
}

trait MessageHandler extends Logging {
  def queueClient: QueueClient

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
    logger.info(s"trying to parse ")
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
    //TODO DONT LOG THE SECRETS!
    logger.info(s"token is $maybeApiClientToken")
    logger.info(s"clientid is $maybeApiClientId")
    logger.info(s"expected token is ${Config.apiToken}")
    logger.info(s"expected client id is ${Config.apiClientId}")
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

  def handleRequest(inputStream: InputStream, outputStream: OutputStream, context: Context): Unit = {

    logger.info(s"Salesforce message handler lambda ${Config.stage} is starting up...")
    val queueName = s"salesforce-outbound-messages-${Config.stage}"
    val inputEvent = Json.parse(inputStream)

    if (credentialsAreValid(inputEvent)) {

      logger.info("Authenticated request successfully...")
      val body = (inputEvent \ "body").as[String]

      val notifications = parseMessage(body)
      //map here and deal with the list of futures later!
      notifications.foreach { notification =>
        val queueMessage = QueueMessage(notification.getSObject.getId)
        val queueMessageString = Json.prettyPrint(Json.toJson(queueMessage))
        //todo fix this to work with multiple notifications IT SHOULD NOT RETURN SUCCESS AFTER THE FIRST ONE
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

}

object Lambda extends MessageHandler with RealDependencies