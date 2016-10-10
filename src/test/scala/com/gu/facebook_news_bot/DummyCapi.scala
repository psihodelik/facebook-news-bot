package com.gu.facebook_news_bot

import com.gu.contentapi.client.model.v1.Content
import com.gu.facebook_news_bot.services.{Capi, Topic}
import com.gu.facebook_news_bot.util.JsonHelpers
import com.gu.contentapi.json.CirceDecoders._

import scala.concurrent.Future

object DummyCapi extends Capi {

  def getHeadlines(front: String, topic: Option[Topic]): Future[Seq[Content]] = {
    getFromFile("headlines", front, topic)
  }

  def getMostViewed(front: String, topic: Option[Topic]): Future[Seq[Content]] = {
    getFromFile("mostviewed", front, topic)
  }

  private def getFromFile(`type`: String, front: String, topic: Option[Topic]): Future[Seq[Content]] = {
    val file = s"src/test/resources/capiResponses/${`type`}-$front${topic.map(_.name).getOrElse("")}.json"
    val result = JsonHelpers.decodeFromFile[Seq[Content]](file)

    Future.successful(result)
  }
}
