package com.gu.facebook_news_bot.briefing

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpMethods, HttpRequest}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import com.github.benmanes.caffeine.cache.Caffeine
import com.gu.facebook_news_bot.BotConfig
import com.gu.facebook_news_bot.models.{Id, MessageToFacebook, User}
import com.gu.facebook_news_bot.state.StateHandler.Result
import com.gu.facebook_news_bot.utils.FacebookMessageBuilder
import de.heikoseeberger.akkahttpcirce.CirceSupport
import org.joda.time.{DateTime, DateTimeZone}
import io.circe.generic.auto._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try

/**
  * Morning briefings created using the Collections tool
  */

case class CollectionsResponse(webTitle: String, collections: Seq[Collection]) {
  def getCollection(displayName: String): Option[Collection] = {
    collections.find(_.displayName == displayName)
  }
}
case class Collection(displayName: String, content: Seq[CollectionsContent]) {
  def getTimestamp: Option[DateTime] = {
    //Find the most recent timestamp
    content.headOption.map { head =>
      val newestItem = content.fold(head)((newest, item) => if (item.frontPublicationDate > newest.frontPublicationDate) item else newest)
      new DateTime(newestItem.frontPublicationDate, DateTimeZone.UTC)
    }
  }
}
case class CollectionsContent(headline: String, trailText: String, thumbnail: String, id: String, frontPublicationDate: Long)

object CollectionsBriefing extends CirceSupport {
  val VariantName = "collections"
  val EditionToCollectionName = Map("uk" -> "morning-briefing")
  val OldNewsThresholdHours = 4

  implicit val system = ActorSystem("collections-actor-system")
  implicit val materializer = ActorMaterializer()

  /**
    * Returns a morning briefing if the following conditions are met:
    * 1. The user's ID puts them in the test group for variant B
    * 2. The user's edition is supported
    * 3. A collection can be retrieved from the cache or the NextGenApi
    * 4. The collection is under OldNewsThresholdHours hours old, because old new is bad news.
    */
  def getBriefing(user: User): Future[Option[Result]] = {
    val lastN: Float = Try(user.ID.takeRight(1).toFloat).getOrElse(9f)
    if (lastN / 10f < BotConfig.variantBProportion && EditionToCollectionName.contains(user.front)) {

      val futureCollection: Future[Option[CachedCollection]] =
        CollectionsBriefingCache.get(user.front).map(cached => Future.successful(Some(cached)))
        .getOrElse(getCollection(user))

      futureCollection.map { maybeCollection =>
        maybeCollection.flatMap { collection =>
          //Check it's not old news
          if (DateTime.now(DateTimeZone.UTC).minusHours(OldNewsThresholdHours).isBefore(collection.timestamp)) {
            Some((user, collection.messages))
          } else None
        }
      }

    } else Future.successful(None)
  }

  private def getCollection(user: User): Future[Option[CachedCollection]] = {
    requestCollection(EditionToCollectionName(user.front)).map { collectionsResponse =>

      val maybeCollection: Option[CachedCollection] = for {
        greetingCollection <- collectionsResponse.getCollection("greeting-message")
        greetingContent <- greetingCollection.content.headOption
        contentCollection <- collectionsResponse.getCollection("morning-briefing")
        if contentCollection.content.nonEmpty
        timestamp <- contentCollection.getTimestamp
      } yield {
        val greetingMessage = MessageToFacebook.textMessage(user.ID, greetingContent.headline)
        val carouselMessage = MessageToFacebook(
          recipient = Id(user.ID),
          message = Some(buildCarousel(user.front, contentCollection))
        )
        CachedCollection(timestamp, List(greetingMessage, carouselMessage))
      }

      maybeCollection.foreach { collection =>
        CollectionsBriefingCache.put(user.front, collection)
      }
      maybeCollection
    }
  }

  private def requestCollection(name: String): Future[CollectionsResponse] = {
    val responseFuture = Http().singleRequest(
      request = HttpRequest(
        method = HttpMethods.GET,
        uri = s"${BotConfig.nextGenApiUrl}/$name/lite.json"
      )
    )
    for {
      response <- responseFuture
      collectionsResponse <- Unmarshal(response.entity).to[CollectionsResponse]
    } yield collectionsResponse
  }

  private def buildCarousel(edition: String, collection: Collection): MessageToFacebook.Message = {
    val tiles = collection.content.take(10).map { content =>
      MessageToFacebook.Element(
        title = content.headline,
        item_url = Some(buildUrl(content.id)),
        subtitle = Some(content.trailText),
        image_url = Some(content.thumbnail),
        buttons = Some(List(MessageToFacebook.Button(`type` = "element_share")))
      )
    }
    val attachment = MessageToFacebook.Attachment.genericAttachment(tiles)

    MessageToFacebook.Message(
      attachment = Some(attachment),
      quick_replies = Some(List(MessageToFacebook.QuickReply(
        content_type = "text",
        title = Some("Popular stories"),
        payload = Some("popular")
      )) ++ FacebookMessageBuilder.topicQuickReplies(edition))
    )
  }

  private def buildUrl(id: String) = s"https://www.theguardian.com/$id?CMP=${BotConfig.campaignCode}&variant=$VariantName"
}

case class CachedCollection(timestamp: DateTime, messages: List[MessageToFacebook])

object CollectionsBriefingCache {
  private val CacheExpireMinutes = 2

  private val cache = Caffeine.newBuilder()
    .expireAfterWrite(CacheExpireMinutes, TimeUnit.MINUTES)
    .build[String, CachedCollection]()

  def get(edition: String): Option[CachedCollection] = Option(cache.getIfPresent(edition))
  def put(edition: String, results: CachedCollection): Unit = cache.put(edition, results)
}
