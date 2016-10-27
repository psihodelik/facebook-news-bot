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

  val campaignCode = getStringOrDefault("campaignCode", "fb_newsbot")

  object aws {
    val usersTable = getMandatoryString("aws.dynamo.usersTableName")
    val region = getStringOrDefault("aws.region", "eu-west-1")

    val CredentialsProvider = new AWSCredentialsProviderChain(
      new ProfileCredentialsProvider("capi"),
      new ProfileCredentialsProvider(),
      new InstanceProfileCredentialsProvider()
    )

    val morningBriefingSQSName = getMandatoryString("aws.sqs.morningBriefingSQSName")
  }

  object facebook {
    val url = getMandatoryString("facebook.url")
    val accessToken = getMandatoryString("facebook.accessToken")
    val secret = getMandatoryString("facebook.secret")
  }

  object capi {
    val key = getMandatoryString("capi.key")
  }

  private def getMandatoryString(name: String): String = {
    Try(config.getString(name)).getOrElse(sys.error(s"Error - missing mandatory config item, $name"))
  }
  private def getStringOrDefault(name: String, default: String): String = {
    Try(config.getString(name)).getOrElse(default)
  }
  private def getMandatoryInt(name: String): Int = {
    Try(config.getInt(name)).getOrElse(sys.error(s"Error - missing mandatory config item, $name"))
  }
}
