package com.gu.facebook_news_bot

import com.amazonaws.auth.{AWSCredentialsProviderChain, InstanceProfileCredentialsProvider}
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.gu.cm.{Configuration, Mode}
import com.typesafe.config.Config

import scala.util.Try

object BotConfig {
  val stage: Mode = sys.env.getOrElse("STAGE", "DEV") match {
    case "DEV" => Mode.Dev
    case _ => Mode.Prod   //CODE or PROD
  }

  val config: Config = Configuration("facebook-news-bot.properties", stage).load

  val port = getMandatoryInt("port")

  val defaultImageUrl = getMandatoryString("defaultImageUrl")
  val supportersImageUrl = getMandatoryString("supportersImageUrl")

  val campaignCode = getStringOrDefault("campaignCode", "fb_newsbot")

  object aws {
    val usersTable = getMandatoryString("aws.dynamo.usersTableName")
    val userTeamTable = getMandatoryString("aws.dynamo.userTeamTableName")
    val userNomsTable = getMandatoryString("aws.dynamo.userNomsTableName")
    val region = getStringOrDefault("aws.region", "eu-west-1")

    val loggingKinesisStreamName: Option[String] = Try(config.getString("aws.logging.kinesisStreamName")).toOption

    val CredentialsProvider = new AWSCredentialsProviderChain(
      new ProfileCredentialsProvider("capi"),
      new ProfileCredentialsProvider(),
      InstanceProfileCredentialsProvider.getInstance()
    )

    val morningBriefingSQSName = getMandatoryString("aws.sqs.morningBriefingSQSName")
  }

  object facebook {
    val host = getMandatoryString("facebook.host")
    val version = getMandatoryString("facebook.version")
    val port = getIntOrDefault("facebook.port", 443)
    val accessToken = getMandatoryString("facebook.accessToken")
    val secret = getMandatoryString("facebook.secret")
    //Facebook must be https, but if running locally against a test service we need the option
    val protocol = if (stage == Mode.Dev) "http" else "https"
    val appId = getMandatoryString("facebook.appId")
    val pageId = getMandatoryString("facebook.pageId")
  }

  object capi {
    val key = getMandatoryString("capi.key")
  }

  object football {
    val enabled = getBoolOrDefault("football.enabled", false)
    val interactiveUrl = getMandatoryString("football.interactiveUrl")
    val api = getMandatoryString("football.sheetsApi")
    val transfersSQSName = getMandatoryString("football.transfersSQSName")
    val defaultImageUrl = getMandatoryString("football.defaultImageUrl")
    val rumoursSQSName = getMandatoryString("football.rumoursSQSName")
    val feedbackEnabled = getBoolOrDefault("football.feedbackEnabled", false)
  }

  val nextGenApiUrl = {
    if (stage == Mode.Dev) ""
    else getMandatoryString("nextGenApiUrl")
  }

  //Proportion of users in test variant B
  val variantBProportion = getDoubleOrDefault("variantBProportion", 0.5)

  private def getMandatoryString(name: String): String = {
    Try(config.getString(name)).getOrElse(if (stage == Mode.Dev) "" else sys.error(s"Error - missing mandatory config item, $name"))
  }
  private def getStringOrDefault(name: String, default: String): String = {
    Try(config.getString(name)).getOrElse(default)
  }
  private def getMandatoryInt(name: String): Int = {
    Try(config.getInt(name)).getOrElse(if (stage == Mode.Dev) 0 else sys.error(s"Error - missing mandatory config item, $name"))
  }
  private def getIntOrDefault(name: String, default: Int): Int = {
    Try(config.getInt(name)).getOrElse(default)
  }
  private def getDoubleOrDefault(name: String, default: Double): Double = {
    Try(config.getDouble(name)).getOrElse(default)
  }
  private def getBoolOrDefault(name: String, default: Boolean): Boolean = {
    Try(config.getBoolean(name)).getOrElse(default)
  }
}
