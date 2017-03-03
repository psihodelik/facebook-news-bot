package com.gu.facebook_news_bot.briefing

import akka.actor.Props
import com.gu.facebook_news_bot.BotConfig
import com.gu.facebook_news_bot.briefing.MorningBriefingPoller._
import com.gu.facebook_news_bot.models.{Id, MessageToFacebook, User}
import com.gu.facebook_news_bot.services.Facebook.FacebookMessageResult
import com.gu.facebook_news_bot.services.{Capi, Facebook, FacebookEvents, SQSMessageBody}
import com.gu.facebook_news_bot.state.MainState
import com.gu.facebook_news_bot.state.StateHandler._
import com.gu.facebook_news_bot.stores.UserStore
import com.gu.facebook_news_bot.utils.Loggers._
import com.gu.facebook_news_bot.utils._
import io.circe.generic.auto._

import scala.concurrent.Future

object MorningBriefingPoller {
  def props(userStore: UserStore, capi: Capi, facebook: Facebook) = Props(new MorningBriefingPoller(userStore, capi, facebook))

  case class BriefingEventLog(id: String, event: String = "morning-briefing", _eventName: String = "morning-briefing", variant: String) extends LogEvent
  def logBriefing(id: String, variant: String): Unit = {
    val eventLog = BriefingEventLog(id = id, variant = variant)
    logEvent(JsonHelpers.encodeJson(eventLog))
    FacebookEvents.logEvent(eventLog)
  }

  def morningMessage(user: User) = MessageToFacebook.textMessage(user.ID, ResponseText.morningBriefing)
}

class MorningBriefingPoller(val userStore: UserStore, val capi: Capi, val facebook: Facebook) extends SQSPoller {
  val SQSName = BotConfig.aws.morningBriefingSQSName

  override def process(messageBody: SQSMessageBody): Future[List[FacebookMessageResult]] = {
    JsonHelpers.decodeJson[User](messageBody.Message).map { userFromSqs: User =>
      for {
        //Get the latest version of the user from dynamodb
        maybeUser: Option[User] <- Notifier.getUser(userFromSqs.ID, facebook, userStore)
        result: List[FacebookMessageResult] <- {
          maybeUser.map { user =>
            getMessages(user).flatMap { case (updatedUser, messages) =>
              Notifier.sendAndUpdate(updatedUser, messages, facebook, userStore)
            }
          } getOrElse Future.successful(Nil)
        }
      } yield result
    } getOrElse Future.successful(Nil)
  }

  private def getMessages(user: User): Future[Result] = {
    appLogger.debug(s"Getting morning briefing for User: $user")

    val futureMaybeCarousel: Future[Option[MessageToFacebook.Message]] = {
      if (user.briefingTopic1.nonEmpty) {
        logBriefing(user.ID, CustomBriefing.getVariant(user.front))

        CustomBriefing.getBriefing(user, capi)
      } else {
        val variant = s"editors-picks-${user.front}"
        logBriefing(user.ID, variant)

        capi.getHeadlines(user.front, None).map { contentList =>
          FacebookMessageBuilder.contentToCarousel(
            contentList = contentList,
            offset = 0,
            edition = user.front,
            currentTopic = None,
            variant = Some(variant))
        }
      }
    }

    futureMaybeCarousel map {
      case Some(carousel) =>
        val updatedCarousel = carousel //TODO - add special tile to start of carousel
        val carouselMessage = MessageToFacebook(Id(user.ID), Some(updatedCarousel))

        val messages = List(morningMessage(user), carouselMessage)

        val updatedUser = user.copy(
          state = Some(MainState.Name),
          contentTopic = None
        )

        (updatedUser, messages)

      case None => (user, Nil)
    }
  }
}
