package com.gu.salesfoce.messageHandler

import java.io.{ OutputStream, OutputStreamWriter }
import com.gu.salesfoce.messageHandler.ResponseWriters._

import com.gu.salesfoce.messageHandler.ResponseModels.{ ApiResponse, Headers }
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
    logger.info(s"Response will be: \n ${jsonResponse.toString}")
    writer.write(Json.stringify(jsonResponse))
    writer.close()
  }
  //TODO IF WE KEEP THE FAILURE EMAIL LAMBDAS HERE WE SHOULD RENAME THESE TO SOMETHING MORE GENERIC
  //  val successfulCancellation = AutoCancelResponse("200", new Headers, "Success")
  //  def noActionRequired(reason: String) = AutoCancelResponse("200", new Headers, s"Auto-cancellation is not required: $reason")
  //
  //  val unauthorized = AutoCancelResponse("401", new Headers, "Credentials are missing or invalid")
  //  val badRequest = AutoCancelResponse("400", new Headers, "Failure to parse XML successfully")
  //  def internalServerError(error: String) = AutoCancelResponse("500", new Headers, s"Failed to process auto-cancellation with the following error: $error")

}