package com.gu.facebook_news_bot.utils

import com.amazonaws.services.s3.AmazonS3Client
import com.gu.cm.Mode
import com.gu.facebook_news_bot.BotConfig

object BadWords {
  private val words: Set[String] = {
    if (BotConfig.stage == Mode.Dev) Set("sausage")
    else {
      val s3Client: AmazonS3Client = new AmazonS3Client()
      val stream = s3Client.getObject(BotConfig.badWordsBucket, BotConfig.badWordsFile).getObjectContent

      val result = scala.io.Source.fromInputStream(stream).getLines.filter(_.nonEmpty).map(_.toLowerCase).toSet

      stream.close()

      result
    }
  }

  def isBad(s: String): Boolean = words.contains(s.toLowerCase)

  def warmUp: Unit = isBad("warm")
}
