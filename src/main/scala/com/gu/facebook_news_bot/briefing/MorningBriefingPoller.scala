package com.gu.facebook_news_bot.briefing

import java.util.concurrent.Executors

import akka.actor.{Actor, Props}
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException
import com.amazonaws.services.sqs.model.{DeleteMessageBatchRequest, DeleteMessageBatchRequestEntry, Message, ReceiveMessageRequest}
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.gu.facebook_news_bot.BotConfig
import com.gu.facebook_news_bot.briefing.MorningBriefingPoller.{Poll, logBriefing}
import com.gu.facebook_news_bot.models.{MessageToFacebook, User}
import com.gu.facebook_news_bot.services.Facebook._
import com.gu.facebook_news_bot.services.{Capi, Facebook, SQS, SQSMessageBody}
import com.gu.facebook_news_bot.state.MainState
import com.gu.facebook_news_bot.state.StateHandler.Result
import com.gu.facebook_news_bot.stores.UserStore
import com.gu.facebook_news_bot.utils.{JsonHelpers, ResponseText}
import com.gu.facebook_news_bot.utils.Loggers._
import io.circe.generic.auto._
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

object MorningBriefingPoller {
  def props(userStore: UserStore, capi: Capi, facebook: Facebook) = Props(new MorningBriefingPoller(userStore, capi, facebook))

  case object Poll

  case class BriefingEventLog(id: String, event: String = "morning-briefing", variant: String)
  def logBriefing(id: String, variant: String): Unit =
    logEvent(JsonHelpers.encodeJson(BriefingEventLog(id = id, variant = variant)))
}

class MorningBriefingPoller(userStore: UserStore, capi: Capi, facebook: Facebook) extends Actor {

  private val MaxBatchSize = 10 //Max allowed by SQS
  private val PollPeriod = 500.millis

  implicit val exec = ExecutionContext.fromExecutorService(
    Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("morning-briefing-poller-%d").build())
  )

  override def preStart(): Unit = self ! Poll

  def receive = {
    case Poll =>

      val request = new ReceiveMessageRequest(BotConfig.aws.morningBriefingSQSName)
        .withMaxNumberOfMessages(MaxBatchSize)
        .withWaitTimeSeconds(10)

      val messages: Seq[Message] = Try(SQS.client.receiveMessage(request)) match {
        case Success(result) => result.getMessages.asScala
        case Failure(e) =>
          appLogger.error(s"Error polling SQS queue: ${e.getMessage}", e)
          Nil
      }

      val decodedMessages = messages.flatMap(message => JsonHelpers.decodeJson[SQSMessageBody](message.getBody))
      Future.sequence(decodedMessages.flatMap { decodedMessage =>
        JsonHelpers.decodeJson[User](decodedMessage.Message).map(processUser)
      }).foreach { _ =>
        //Resume polling only once the requests have been sent
        self ! Poll
      }

      //Delete items from the queue
      if (messages.nonEmpty) {
        val deleteRequest = new DeleteMessageBatchRequest(
          BotConfig.aws.morningBriefingSQSName,
          messages.map(message => new DeleteMessageBatchRequestEntry(message.getMessageId, message.getReceiptHandle)).asJava
        )
        Try(SQS.client.deleteMessageBatch(deleteRequest)) recover {
          case e => appLogger.warn(s"Failed to delete messages from queue: ${e.getMessage}", e)
        }
      } else context.system.scheduler.scheduleOnce(PollPeriod, self, Poll)

    case _ =>
      appLogger.warn("Unknown message received by MorningBriefingPoller")
  }

  private def processUser(user: User): Future[List[FacebookMessageResult]] = {
    facebook.getUser(user.ID) flatMap {
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
        val daysUncontactable = user.daysUncontactable.map(_+1).getOrElse(1)
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

        MainState.getHeadlines(user, capi, Some(variant)) map { case (updatedUser, messages) =>
          (updatedUser, morningMessage(updatedUser) :: messages)
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

  private def morningMessage(user: User) = MessageToFacebook.textMessage(user.ID, ResponseText.morningBriefing)
}
