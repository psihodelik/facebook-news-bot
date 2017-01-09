package com.gu.facebook_news_bot.football_transfers

import java.util.concurrent.TimeUnit

import akka.actor.Props
import com.github.benmanes.caffeine.cache.Caffeine
import com.gu.contentapi.client.model.v1.Content
import com.gu.facebook_news_bot.BotConfig
import com.gu.facebook_news_bot.models.{Id, MessageToFacebook, User}
import com.gu.facebook_news_bot.services.Facebook.FacebookMessageResult
import com.gu.facebook_news_bot.services.{Capi, Facebook, FacebookEvents, SQSMessageBody}
import com.gu.facebook_news_bot.utils.Loggers.LogEvent
import com.gu.facebook_news_bot.utils.{FacebookMessageBuilder, JsonHelpers, Notifier, SQSPoller}
import com.gu.facebook_news_bot.utils.Loggers._
import com.gu.facebook_news_bot.football_transfers.FootballTransferRumoursPoller._
import com.gu.facebook_news_bot.stores.UserStore
import org.jsoup.Jsoup
import io.circe.generic.auto._

import scala.concurrent.Future
import scala.concurrent.duration._

/**
  * Polls SQS for users who should have the latest 'Rumour Mill' article sent to them.
  * Note that the lambda (facebook-news-bot-football-rumours) decides which article to send,
  * and whether an article is fresh (newer than 24 hours).
  */

class FootballTransferRumoursPoller(val facebook: Facebook, val capi: Capi, val userStore: UserStore) extends SQSPoller {
  val SQSName = BotConfig.football.rumoursSQSName
  override val PollPeriod = 2000.millis

  protected def process(messageBody: SQSMessageBody): Future[List[FacebookMessageResult]] = {
    JsonHelpers.decodeJson[UserFootballTransferRumour](messageBody.Message).map { rumour =>

      val futureMaybeUserMessages: Future[(Option[User], scala.List[MessageToFacebook])] = for {
        maybeUser <- Notifier.getUser(rumour.userId, facebook, userStore)
        messages <- getMessages(rumour)
      } yield (maybeUser, messages)

      futureMaybeUserMessages.flatMap {
        case (Some(user), messages @ (x :: tail)) =>
          logNotification(rumour.userId, rumour.articleId)
          Notifier.sendAndUpdate(user, messages, facebook, userStore)
        case _ => Future.successful(Nil)
      }
    }.getOrElse(Future.successful(Nil))
  }

  def getMessages(rumour: UserFootballTransferRumour): Future[List[MessageToFacebook]] = {
    val futureMaybeMessage = FootballTransferRumoursCache.get(rumour.articleId).map(m => Future.successful(Some(m)))
      .getOrElse {
        //Not already cached - get the article from CAPI and build a message
        capi.getArticle(rumour.articleId) map { maybeContent =>
          val maybeMessage = maybeContent.map { content =>
            val message = buildRumourMessage(content)
            FootballTransferRumoursCache.put(rumour.articleId, message)
            message
          }
          maybeMessage
        }
      }

    futureMaybeMessage.map { maybeMessage =>
      maybeMessage
        .map(message => List(MessageToFacebook(recipient = Id(rumour.userId), message = Some(message))))
        .getOrElse(Nil)
    }
  }
}

object FootballTransferRumoursPoller {
  def props(facebook: Facebook, capi: Capi, userStore: UserStore) = Props(new FootballTransferRumoursPoller(facebook, capi, userStore))

  case class NotificationEventLog(id: String, event: String = "football-transfer-rumour-notification", _eventName: String = "football-transfer-rumour-notification", articleId: String) extends LogEvent

  def logNotification(userId: String, articleId: String): Unit = {
    val eventLog = NotificationEventLog(id = userId, articleId = articleId)
    logEvent(JsonHelpers.encodeJson(eventLog))
    FacebookEvents.logEvent(eventLog)
  }

  private def buildRumourMessage(content: Content): MessageToFacebook.Message = {
    val element = MessageToFacebook.Element(
      title = content.webTitle,
      item_url = Some(s"${content.webUrl}?CMP=${BotConfig.campaignCode}"),
      subtitle = content.fields.flatMap(_.standfirst.map(Jsoup.parse(_).text)),
      image_url = Some(FacebookMessageBuilder.getImageUrl(content)),
      buttons = Some(List(
        MessageToFacebook.Button(`type` = "element_share"),
        MessageToFacebook.Button(
          `type` = "web_url",
          url = Some(s"${BotConfig.football.interactiveUrl}?CMP=${BotConfig.campaignCode}"),
          title = Some("Latest transfers")
        )
      ))
    )

    MessageToFacebook.Message(
      attachment = Some(MessageToFacebook.Attachment.genericAttachment(List(element))),
      quick_replies = Some(List(
        MessageToFacebook.QuickReply("text", Some("Headlines"), Some("headlines")),
        MessageToFacebook.QuickReply("text", Some("Football news"), Some("football"))
      ))
    )
  }
}

object FootballTransferRumoursCache {
  private val CacheExpireHours = 24

  private val cache = Caffeine.newBuilder()
    .expireAfterWrite(CacheExpireHours, TimeUnit.HOURS)
    .build[String, MessageToFacebook.Message]()

  def get(articleId: String): Option[MessageToFacebook.Message] = Option(cache.getIfPresent(articleId))
  def put(articleId: String, message: MessageToFacebook.Message): Unit = cache.put(articleId, message)
}
