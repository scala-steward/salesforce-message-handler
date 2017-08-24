package com.gu.salesforce.messageHandler

import java.io.{ OutputStream, OutputStreamWriter }
import com.gu.salesforce.messageHandler.ResponseWriters._

import com.gu.salesforce.messageHandler.ResponseModels.{ ApiResponse, Headers }
import play.api.libs.json.{ Json, Writes }

object ResponseModels {

  case class Headers(contentType: String = "text/xml")

  case class ApiResponse(statusCode: String, headers: Headers, body: String)

}

object ResponseWriters {

  implicit val headersWrites = new Writes[Headers] {
    def writes(headers: Headers) = Json.obj(
      "Content-Type" -> headers.contentType)
  }

  implicit val responseWrites = new Writes[ApiResponse] {
    def writes(response: ApiResponse) = Json.obj(
      "statusCode" -> response.statusCode,
      "headers" -> response.headers,
      "body" -> response.body)
  }

}

object APIGatewayResponse extends Logging {

  def outputForAPIGateway(outputStream: OutputStream, response: ApiResponse): Unit = {
    val writer = new OutputStreamWriter(outputStream, "UTF-8")
    val jsonResponse = Json.toJson(response)
    writer.write(Json.stringify(jsonResponse))
    writer.close()
  }

  val unauthorized = ApiResponse("401", Headers(), "Credentials are missing or invalid")
  val internalServerError = ApiResponse("500", new Headers, "Internal server error")
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
}