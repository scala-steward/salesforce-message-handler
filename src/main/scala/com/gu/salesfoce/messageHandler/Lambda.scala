package com.gu.salesfoce.messageHandler

import com.amazonaws.services.lambda.runtime.Context
import java.io.{ InputStream, OutputStream }

import com.gu.salesfoce.messageHandler.ResponseModels.{ ApiResponse, Headers }
import play.api.libs.json.{ JsValue, Json }

import scala.xml.Elem
import scala.xml.XML._

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
    logger.info(s"Auto-cancel Lambda is starting up...")
    val inputEvent = Json.parse(inputStream)
    val xmlBody = extractXmlBodyFromJson(inputEvent)
    logger.info(s"got input ${xmlBody}")
    APIGatewayResponse.outputForAPIGateway(outputStream, okResponse)
  }

  def extractXmlBodyFromJson(inputEvent: JsValue): Elem = {
    val body = (inputEvent \ "body")
    loadString(body.as[String])
  }
}
