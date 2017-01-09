package com.gu.facebook_news_bot.briefing

import akka.actor.Props
import com.gu.facebook_news_bot.BotConfig
import com.gu.facebook_news_bot.briefing.MorningBriefingPoller._
import com.gu.facebook_news_bot.models.{MessageToFacebook, User}
import com.gu.facebook_news_bot.services.Facebook.FacebookMessageResult
import com.gu.facebook_news_bot.services.{Capi, Facebook, FacebookEvents, SQSMessageBody}
import com.gu.facebook_news_bot.state.MainState
import com.gu.facebook_news_bot.state.StateHandler._
import com.gu.facebook_news_bot.stores.UserStore
import com.gu.facebook_news_bot.utils.Loggers._
import com.gu.facebook_news_bot.utils.{JsonHelpers, Notifier, ResponseText, SQSPoller}
import org.joda.time.{DateTime, DateTimeZone}
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


  private def morningMessage(user: User) = {
    val message = {
      if (isNewYear(user.offsetHours)) "Happy new year! Here are the top stories today"
      else ResponseText.morningBriefing
    }
    MessageToFacebook.textMessage(user.ID, message)
  }

  def isNewYear(offset: Double): Boolean = {
    val hours = math.floor(offset).toInt
    val mins = ((offset * 60) % 60).toInt
    val dateTime = DateTime.now(DateTimeZone.forOffsetHoursMinutes(hours, mins))
    dateTime.getDayOfMonth == 1 && dateTime.getMonthOfYear == 1
  }
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

    CollectionsBriefing.getBriefing(user).flatMap { maybeBriefing: Option[Result] =>
      maybeBriefing.map { briefing =>
        logBriefing(user.ID, CollectionsBriefing.getVariant(user.front))
        Future.successful(briefing)
      }.getOrElse {
        //Fall back on editors-picks briefing
        val variant = s"editors-picks-${user.front}"
        logBriefing(user.ID, variant)

        MainState.getHeadlines(user, capi, Some(variant)) map { case (updatedUser, messages) =>
          (updatedUser, morningMessage(updatedUser) :: messages)
        }
      }
    }
  }
}
