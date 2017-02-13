package com.gu.facebook_news_bot.utils

import com.gu.facebook_news_bot.models.{MessageToFacebook, User}
import com.gu.facebook_news_bot.services.Facebook
import com.gu.facebook_news_bot.services.Facebook.{FacebookMessageResult, GetUserError, GetUserNoDataResponse, GetUserSuccessResponse}
import com.gu.facebook_news_bot.state.FootballTransferStates
import com.gu.facebook_news_bot.stores.UserStore
import com.gu.facebook_news_bot.utils.Loggers._
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * For sending notifications to users.
  *
  * Takes care of:
  * 1. Tracking changing timezones
  * 2. Recording uncontactable users
  */
object Notifier {

  /**
    * Attempts to get the user's data from both Facebook and dynamodb.
    * If Facebook doesn't return their data, record the user as uncontactable and return None.
    * If the user's timezone has changed, update their notification times in dynamodb and return None.
    */
  def getUser(userId: String, facebook: Facebook, userStore: UserStore): Future[Option[User]] = {
    for {
      maybeUser: Option[User] <- userStore.getUser(userId)
      fbResult: Facebook.GetUserResult <- facebook.getUser(userId)
    } yield {
      maybeUser flatMap { user =>
        fbResult match {
          case GetUserSuccessResponse(fbUser) =>
            if (fbUser.timezone == user.offsetHours) {
              Some(user)
            } else {
              //User's timezone has changed - fix this now, but don't send briefing
              val updatedUser = changeNotificationTime(user, fbUser.timezone)
              updateUser(updatedUser, userStore)
              None
            }

          case GetUserNoDataResponse =>
            /**
              * Facebook returned a 200 but will not give us the user's data, which generally means they've deleted the conversation.
              * Mark them as uncontactable
              */
            updateUser(incrementDaysUncontactable(user), userStore)
            None

          case GetUserError(error) =>
            appLogger.info(s"Error from Facebook while trying to get data for user ${user.ID}: $error")
            None
        }
      }
    }
  }

  /**
    * Call this to send a notification to a user and then update their state in dynamodb
    */
  def sendAndUpdate(user: User, messages: List[MessageToFacebook], facebook: Facebook, userStore: UserStore): Future[List[FacebookMessageResult]] = {
    if (messages.nonEmpty) {
      appLogger.debug(s"Sending notification to ${user.ID}: $messages")

      facebook.send(messages) map { results: List[Facebook.FacebookMessageResult] =>
        if (!results.contains(Facebook.FacebookMessageSuccess)) {
          //We were able to get the user's details from FB, but they appear to be uncontactable
          appLogger.debug(s"Failed to send notification to user $user: $messages")
          updateUser(incrementDaysUncontactable(user), userStore)
          results
        } else {
          updateUser(user.copy(daysUncontactable = Some(0)), userStore)
          results
        }
      }
    } else {
      updateUser(user, userStore)
      Future.successful(Nil)
    }
  }

  private val TimeFormat = DateTimeFormat.forPattern("HH:mm")

  private def changeNotificationTime(user: User, timezone: Double): User = {
    val newNotificationTimeUTC: String = {
      if (user.notificationTime != "-") {
        val notifyTime = DateTime.parse(user.notificationTime, TimeFormat)
        notifyTime.minusMinutes((timezone * 60).toInt).toString("HH:mm")
      } else "-"
    }

    val newFootballRumoursTimeUTC: Option[String] = {
      if (user.footballTransfers.contains(true)) Some(FootballTransferStates.rumoursNotificationTime.minusMinutes((timezone * 60).toInt).toString("HH:mm"))
      else None
    }

    user.copy(
      notificationTimeUTC = newNotificationTimeUTC,
      offsetHours = timezone,
      footballRumoursTimeUTC = newFootballRumoursTimeUTC
    )
  }

  private def updateUser(user: User, userStore: UserStore, retry: Int = 0): Unit = {
    userStore.updateUser(user) foreach { updateResult =>
      updateResult.left.toOption.foreach { error =>
        //User has since been updated in dynamo, get the latest version and try again
        if (retry < 3) {
          userStore.getUser(user.ID).foreach {
            case Some(latestUser) =>
              val mergedUser = latestUser.copy(
                //All other fields should come from latestUser
                state = user.state,
                offsetHours = user.offsetHours,
                notificationTime = user.notificationTime,
                notificationTimeUTC = user.notificationTimeUTC,
                footballRumoursTimeUTC = user.footballRumoursTimeUTC,
                daysUncontactable = user.daysUncontactable
              )
              updateUser(mergedUser, userStore, retry + 1)

            case None => updateUser(user, userStore, retry + 1)
          }
        } else {
          //Something has gone very wrong
          appLogger.error(s"Failed to update user state multiple times. User is $user and error is ${error.getMessage}", error)
        }
      }
    }
  }

  private def incrementDaysUncontactable(user: User): User =
    user.copy(daysUncontactable = user.daysUncontactable.map(_ + 1).orElse(Some(1)))
}
