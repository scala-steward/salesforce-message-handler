package com.gu.salesfoce.messageHandler

import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import org.slf4j.{Logger, LoggerFactory}

/**
  * This is compatible with aws' lambda JSON to POJO conversion.
  * You can test your lambda by sending it the following payload:
  * {"name": "Bob"}
  */
class SoapWrapper() {
  var body: String = _

  def geBody(): String = body

  def setBody(theBody: String): Unit = body = theBody

  override def toString: String = body
}

case class Env(app: String, stack: String, stage: String) {
  override def toString: String = s"App: $app, Stack: $stack, Stage: $stage\n"
}

object Env {
  def apply(): Env = Env(
    Option(System.getenv("App")).getOrElse("DEV"),
    Option(System.getenv("Stack")).getOrElse("DEV"),
    Option(System.getenv("Stage")).getOrElse("DEV"))
}

object Lambda extends RequestHandler[SoapWrapper, SoapWrapper] {

  val logger: Logger = LoggerFactory.getLogger(this.getClass)


  override def handleRequest(input: SoapWrapper, context: Context): SoapWrapper = {
    logger.info(s"Starting")
    logger.info(input.toString)
    val response = new SoapWrapper()
    response.setBody("hi")
    logger.info(s"returning ${response.toString}")
    return response
  }

  //  /*
  //   * This is your lambda entry point
  //   */
  //  def handler(lambdaInput: SoapWrapper, context: Context): Unit = {
  //    val env = Env()
  //    logger.info(s"Starting $env")
  //    logger.info(lambdaInput.toString)
  //    logger.info(process("world", env))
  //  }

  /*
   * I recommend to put your logic outside of the handler
   */
  def process(name: String, env: Env): String = s"Hello $name! (from ${env.app} in ${env.stack})\n"

}

//object TestIt {
//  def main(args: Array[String]): Unit = {
//    println(Lambda.process(args.headOption.getOrElse("Alex"), Env()))
//  }
//}
