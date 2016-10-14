package com.gu.facebook_news_bot.briefing

import java.util.concurrent.Executors

import akka.actor.{Actor, Props}
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException
import com.amazonaws.services.sqs.model.{DeleteMessageBatchRequest, DeleteMessageBatchRequestEntry, Message, ReceiveMessageRequest}
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.gu.facebook_news_bot.BotConfig
import com.gu.facebook_news_bot.briefing.MorningBriefingPoller.Poll
import com.gu.facebook_news_bot.models.{MessageToFacebook, User}
import com.gu.facebook_news_bot.services.{Capi, Facebook, SQS, SQSMessageBody}
import com.gu.facebook_news_bot.state.MainState
import com.gu.facebook_news_bot.state.StateHandler.Result
import com.gu.facebook_news_bot.stores.UserStore
import com.gu.facebook_news_bot.utils.{JsonHelpers, ResponseText}
import com.typesafe.scalalogging.StrictLogging
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
}

class MorningBriefingPoller(userStore: UserStore, capi: Capi, facebook: Facebook) extends Actor with StrictLogging {

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
          logger.error(s"Error polling SQS queue: $e")
          Nil
      }

      val decodedMessages = messages.flatMap(message => JsonHelpers.decodeJson[SQSMessageBody](message.getBody))
      decodedMessages.foreach { decodedMessage =>
        JsonHelpers.decodeJson[User](decodedMessage.Message) foreach processUser
      }

      //Delete items from the queue
      if (messages.nonEmpty) {
        val deleteRequest = new DeleteMessageBatchRequest(
          BotConfig.aws.morningBriefingSQSName,
          messages.map(message => new DeleteMessageBatchRequestEntry(message.getMessageId, message.getReceiptHandle)).asJava
        )
        Try(SQS.client.deleteMessageBatch(deleteRequest)) recover {
          case e => logger.warn(s"Failed to delete messages from queue: ${e.getMessage}")
        }
      }

      context.system.scheduler.scheduleOnce(PollPeriod, self, Poll)

    case _ =>
      logger.warn("Unknown message received by MorningBriefingPoller")
  }

  private def processUser(user: User): Unit = {
    facebook.getUser(user.ID) map { fbUser =>
      if (fbUser.timezone == user.offsetHours) {
        getMorningBriefing(user).onComplete {
          case Success((updatedUser, fbMessages)) => updateAndSend(updatedUser, fbMessages)
          case Failure(error) => logger.error(s"Error getting morning briefing for user ${user.ID}: $error")
        }
      } else {
        //User's timezone has changed - fix this now, but don't send briefing
        val updatedUser = updateNotificationTime(user, fbUser.timezone)
        updateAndSend(updatedUser, Nil)
      }
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
    logger.debug(s"Getting morning briefing for User: $user")

    //TODO - add A/B testing switch here
    MainState.getHeadlines(user, capi, variant = user.variant)
  }

  //Update the user in dynamo, then send the messages
  private def updateAndSend(user: User, messages: List[MessageToFacebook], retry: Int = 0): Unit = {
    userStore.updateUser(user) map { updateResult =>
      updateResult.fold(
        { error: ConditionalCheckFailedException =>
          //User has since been updated in dynamo, get the latest version and try again
          if (retry < 3) {
            userStore.getUser(user.ID) foreach { _.foreach { latestUser =>
              val mergedUser = user.copy(
                //All other fields should come from updatedUser
                version = latestUser.version,
                front = latestUser.front
              )
              updateAndSend(mergedUser, messages, retry+1)
            }}
          } else {
            //Something has gone very wrong
            logger.error(s"Failed to update user state multiple times. User is $user and error is $error")
          }
        }, { _ =>
          if (messages.nonEmpty) {
            val briefing = morningMessage(user) :: messages
            logger.debug(s"Sending morning briefing to ${user.ID}: $briefing")
            facebook.send(briefing)
          }
        }
      )
    }
  }

  private def morningMessage(user: User) = MessageToFacebook.textMessage(user.ID, ResponseText.morningBriefing)
}
