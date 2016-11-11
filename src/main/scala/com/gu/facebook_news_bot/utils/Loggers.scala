package com.gu.facebook_news_bot.utils

import io.circe.Json
import org.joda.time.{DateTime, DateTimeZone}
import org.slf4j.LoggerFactory

object Loggers {
  /**
    * eventLogger - populates the event.log file, which contains json-formatted event logging to be sent to logstash
    */
  val eventLogger = LoggerFactory.getLogger("eventLogger")

  /**
    * appLogger - populates the application.log file, which contains warnings/errors
    */
  val appLogger = LoggerFactory.getLogger("appLogger")

  def logEvent(json: Json): Unit = {
    //We only write json to event.log, so insert a timestamp here
    val withTime = json.mapObject(_.add("timestamp_bot", Json.fromString(DateTime.now(DateTimeZone.UTC).toString)))
    eventLogger.info(withTime.noSpaces)
  }
}
