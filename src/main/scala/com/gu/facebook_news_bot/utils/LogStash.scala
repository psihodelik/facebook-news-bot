package com.gu.facebook_news_bot.utils

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.{Logger, LoggerContext}
import ch.qos.logback.core.joran.spi.JoranException
import ch.qos.logback.core.util.StatusPrinter
import com.amazonaws.auth.AWSCredentialsProviderChain
import com.amazonaws.regions.{Region, Regions}
import com.gu.cm.Identity
import com.gu.facebook_news_bot.BotConfig
import com.gu.logback.appender.kinesis.KinesisAppender
import net.logstash.logback.layout.LogstashLayout
import org.slf4j.{LoggerFactory, Logger => SLFLogger}
import com.gu.facebook_news_bot.utils.Loggers.appLogger

import scala.util.control.NonFatal

case class KinesisAppenderConfig(
                                  stream: String,
                                  credentialsProvider: AWSCredentialsProviderChain,
                                  region: Region = Region.getRegion(Regions.fromName(BotConfig.aws.region)),
                                  bufferSize: Int = 1000)

object LogStash {

  // assume SLF4J is bound to logback in the current environment
  lazy val context = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]

  def makeCustomFields(customFields: Map[String, String]): String = {
    "{" + (for ((k, v) <- customFields) yield (s""""${k}":"${v}"""")).mkString(",") + "}"
  }

  def makeLayout(customFields: String) = {
    val l = new LogstashLayout()
    l.setCustomFields(customFields)
    l
  }

  def makeKinesisAppender(layout: LogstashLayout, context: LoggerContext, appenderConfig: KinesisAppenderConfig) = {
    val a = new KinesisAppender[ILoggingEvent]()
    a.setStreamName(appenderConfig.stream)
    a.setRegion(appenderConfig.region.getName)
    a.setCredentialsProvider(appenderConfig.credentialsProvider)
    a.setBufferSize(appenderConfig.bufferSize)

    a.setContext(context)
    a.setLayout(layout)

    layout.start()
    a.start()
    a
  }

  def init(config: KinesisAppenderConfig) {

    val Facts: Map[String, String] = try {
      val facts: Map[String, String] = {
        appLogger.info("Loading facts from AWS instance tags")
        val identity = Identity.whoAmI("facebook-news-bot", BotConfig.stage)
        Map("app" -> identity.app, "stack" -> identity.stack, "stage" -> identity.stage)
      }
      appLogger.info(s"Using facts: $facts")
      facts
    } catch {
      case NonFatal(e) =>
        appLogger.error("Failed to get facts", e)
        Map.empty
    }

    try {
      appLogger.info("Configuring logging to send to LogStash")
      val layout = makeLayout(makeCustomFields(Facts))
      val appender = makeKinesisAppender(layout, context, config)
      val rootLogger = LoggerFactory.getLogger(SLFLogger.ROOT_LOGGER_NAME).asInstanceOf[Logger]
      rootLogger.addAppender(appender)
      appLogger.info("LogStash configuration completed")
    } catch {
      case e: JoranException => // ignore, errors will be printed below
      case NonFatal(e) =>
        appLogger.error("Error while initialising LogStash", e)
    }

    StatusPrinter.printInCaseOfErrorsOrWarnings(context)
  }
}