package com.gu.facebook_news_bot.services

import com.amazonaws.ClientConfiguration
import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.services.sqs.AmazonSQSClient
import com.gu.facebook_news_bot.BotConfig

case class SQSMessageBody(MessageId: String, Message: String)

object SQS {
  val client = {
    val sqsClientConfiguration = new ClientConfiguration().withConnectionTimeout(20000).withSocketTimeout(20000)
    val sqsClient = new AmazonSQSClient(BotConfig.aws.CredentialsProvider, sqsClientConfiguration)
    sqsClient.setRegion(Region.getRegion(Regions.fromName(BotConfig.aws.region)))
    sqsClient
  }
}
