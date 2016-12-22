package com.gu.facebook_news_bot.briefing

import akka.actor.Props
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException
import com.gu.facebook_news_bot.BotConfig
import com.gu.facebook_news_bot.briefing.MorningBriefingPoller._
import com.gu.facebook_news_bot.models.{MessageToFacebook, User}
import com.gu.facebook_news_bot.services.Facebook.{FacebookMessageResult, GetUserError, GetUserNoDataResponse, GetUserSuccessResponse}
import com.gu.facebook_news_bot.services.{Capi, Facebook, FacebookEvents, SQSMessageBody}
import com.gu.facebook_news_bot.state.{FootballTransferStates, MainState}
import com.gu.facebook_news_bot.state.StateHandler._
import com.gu.facebook_news_bot.stores.UserStore
import com.gu.facebook_news_bot.utils.Loggers._
import com.gu.facebook_news_bot.utils.{JsonHelpers, ResponseText, SQSPoller}
import org.joda.time.{DateTime, DateTimeZone}
import org.joda.time.format.DateTimeFormat
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
      if (isNewYear(DateTime.now(DateTimeZone.UTC))) "Happy new year! Here are the top stories today"
      else ResponseText.morningBriefing
    }
    MessageToFacebook.textMessage(user.ID, message)
  }

  private def isNewYear(dateTime: DateTime) = dateTime.getDayOfMonth == 1 && dateTime.getMonthOfYear == 1
}

class MorningBriefingPoller(val userStore: UserStore, val capi: Capi, val facebook: Facebook) extends SQSPoller {
  val SQSName = BotConfig.aws.morningBriefingSQSName
  
  override def process(messageBody: SQSMessageBody): Future[List[FacebookMessageResult]] =
    JsonHelpers.decodeJson[User](messageBody.Message).map(user => processUser(user.ID)) getOrElse Future.successful(Nil)

  private def processUser(userId: String): Future[List[FacebookMessageResult]] = {
    for {
      maybeUser <- userStore.getUser(userId)
      fbResult <- facebook.getUser(userId)
      result <- maybeUser.map(user => processUserResults(user, fbResult)).getOrElse(Future.successful(Nil))
    } yield result
  }

  private def processUserResults(user: User, fbResult: Facebook.GetUserResult) = {
    fbResult match {
      case GetUserSuccessResponse(fbUser) =>
        if (fbUser.timezone == user.offsetHours) {
          getMorningBriefing(user).flatMap { case (updatedUser, fbMessages) =>
            updateAndSend(updatedUser, fbMessages)
          } recover { case error =>
            appLogger.error(s"Error getting morning briefing for user ${user.ID}: ${error.getMessage}", error)
            Nil
          }
        } else {
          //User's timezone has changed - fix this now, but don't send briefing
          val updatedUser = updateNotificationTime(user, fbUser.timezone)
          updateAndSend(updatedUser, Nil)
        }

      case GetUserNoDataResponse =>
        /**
          * Facebook returned a 200 but will not give us the user's data, which generally means they've deleted the conversation.
          * Mark them as uncontactable
          */
        val daysUncontactable = user.daysUncontactable.map(_ + 1).getOrElse(1)
        userStore.updateUser(user.copy(daysUncontactable = Some(daysUncontactable)))
        Future.successful(Nil)

      case GetUserError(error) =>
        appLogger.info(s"Error from Facebook while trying to get data for user ${user.ID}: $error")
        Future.successful(Nil)
    }
  }

  private def updateNotificationTime(user: User, timezone: Double): User = {
    val notifyTime = DateTime.parse(user.notificationTime, DateTimeFormat.forPattern("HH:mm"))
    val notifyTimeUTC = notifyTime.minusMinutes((timezone * 60).toInt)
    user.copy(
      notificationTimeUTC = notifyTimeUTC.toString("HH:mm"),
      offsetHours = timezone
    )
  }

  private def getMorningBriefing(user: User): Future[Result] = {
    appLogger.debug(s"Getting morning briefing for User: $user")

    CollectionsBriefing.getBriefing(user).flatMap { maybeBriefing: Option[Result] =>
      maybeBriefing.map { briefing =>
        logBriefing(user.ID, CollectionsBriefing.getVariant(user.front))
        Future.successful(briefing)
      }.getOrElse {
        //Fall back on editors-picks briefing
        val variant = s"editors-picks-${user.front}"
        logBriefing(user.ID, variant)

        //TODO - revert after 2017-01-01
        if (isNewYear(DateTime.now(DateTimeZone.UTC)) && !user.footballTransfers.contains(true)) {
          //Send morning briefing then ask about football transfers subscription
          for {
            (_, headlinesMessages) <- MainState.getHeadlines(user, capi, Some(variant))
            (updatedUser, transfersMessages) <- FootballTransferStates.InitialQuestionState.question(
              user,
              Some("Today the January football transfer window opens! I can send you the rumours and confirmed transfers for your favourite teams. Would you like to subscribe to these updates?")
            )
          } yield (updatedUser, morningMessage(updatedUser) :: headlinesMessages ::: transfersMessages)
        } else {
          MainState.getHeadlines(user, capi, Some(variant)) map { case (updatedUser, messages) =>
            (updatedUser, morningMessage(updatedUser) :: messages)
          }
        }
      }
    }
  }

  //Update the user in dynamo, then send the messages
  private def updateAndSend(user: User, messages: List[MessageToFacebook], retry: Int = 0): Future[List[FacebookMessageResult]] = {
    userStore.updateUser(user.copy(daysUncontactable = Some(0))) flatMap { updateResult =>
      updateResult.fold(
        { error: ConditionalCheckFailedException =>
          //User has since been updated in dynamo, get the latest version and try again
          if (retry < 3) {
            userStore.getUser(user.ID).flatMap {
              case Some(latestUser) =>
                val mergedUser = user.copy(
                  //All other fields should come from updatedUser
                  version = latestUser.version,
                  front = latestUser.front
                )
                updateAndSend(mergedUser, messages, retry+1)

              case None => updateAndSend(user, messages, retry+1)
            }
          } else {
            //Something has gone very wrong
            appLogger.error(s"Failed to update user state multiple times. User is $user and error is ${error.getMessage}", error)
            Future.successful(Nil)
          }
        }, { _ =>
          if (messages.nonEmpty) {
            appLogger.debug(s"Sending morning briefing to ${user.ID}: $messages")
            facebook.send(messages)
          } else Future.successful(Nil)
        }
      )
    }
  }
}
