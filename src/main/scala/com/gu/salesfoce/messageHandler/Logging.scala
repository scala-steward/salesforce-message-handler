package com.gu.salesfoce.messageHandler

import org.slf4j.{ Logger, LoggerFactory }

trait Logging {
  val logger: Logger = LoggerFactory.getLogger(this.getClass)
}
