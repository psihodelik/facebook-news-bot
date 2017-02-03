package com.gu.facebook_news_bot.utils

import java.util.concurrent.Executors

import akka.actor.{Actor, Kill}
import com.amazonaws.services.sqs.model.{DeleteMessageBatchRequest, DeleteMessageBatchRequestEntry, Message, ReceiveMessageRequest}
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.gu.facebook_news_bot.services.Facebook.FacebookMessageResult
import com.gu.facebook_news_bot.services._
import com.gu.facebook_news_bot.utils.Loggers._
import com.gu.facebook_news_bot.utils.SQSPoller.Poll
import io.circe.generic.auto._
import org.joda.time.{DateTime, DateTimeZone}

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

object SQSPoller {
  case object Poll

  //Given the UTC offset, is it currently this date?
  def isDate(offset: Double, date: DateTime): Boolean = {
    val hours = math.floor(offset).toInt
    val mins = ((offset * 60) % 60).toInt
    val now = DateTime.now(DateTimeZone.forOffsetHoursMinutes(hours, mins))

    now.getDayOfMonth == date.getDayOfMonth &&
      now.getMonthOfYear == date.getMonthOfYear &&
      now.getYear == date.getYear
  }
}

trait SQSPoller extends Actor {
  val facebook: Facebook

  val PollPeriod: FiniteDuration = 500.millis
  val MaxBatchSize = 10 //Max allowed by SQS
  val SQSName: String

  implicit val exec = ExecutionContext.fromExecutorService(
    Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("sqs-poller-%d").build())
  )

  override def preStart(): Unit = self ! Poll

  override def postStop(): Unit = appLogger.warn(s"SQSPoller actor for queue $SQSName is stopping")

  def receive = {
    case Poll =>

      val request = new ReceiveMessageRequest(SQSName)
        .withMaxNumberOfMessages(MaxBatchSize)
        .withWaitTimeSeconds(10)

      val messages: Seq[Message] = Try(SQS.client.receiveMessage(request)) match {
        case Success(result) => result.getMessages.asScala
        case Failure(e) =>
          appLogger.error(s"Error polling SQS queue: ${e.getMessage}", e)
          Nil
      }

      val decodedMessages = messages.flatMap(message => JsonHelpers.decodeJson[SQSMessageBody](message.getBody))
      if (decodedMessages.nonEmpty) {
        Future.sequence(decodedMessages.map(process)).onComplete {
          //Resume polling only once the requests have been sent
          case Success(result) => self ! Poll
          case Failure(e) =>
            appLogger.warn(s"Error processing messages from queue $SQSName: ${e.getMessage}", e)
            self ! Poll
        }
      } else context.system.scheduler.scheduleOnce(PollPeriod, self, Poll)

      if (messages.nonEmpty) deleteMessages(messages)

    case _ => appLogger.warn("Unknown message received by SQS Poller")
  }

  protected def process(messageBody: SQSMessageBody): Future[List[FacebookMessageResult]]

  private def deleteMessages(messages: Seq[Message], retry: Int = 0): Unit = {
    val deleteRequest = new DeleteMessageBatchRequest(
      SQSName,
      messages.map(message => new DeleteMessageBatchRequestEntry(message.getMessageId, message.getReceiptHandle)).asJava
    )

    def onFailure(failedMessages: Seq[Message]): Unit = {
      if (retry < 3) {
        deleteMessages(failedMessages, retry + 1)
      } else {
        //If we can't delete messages from SQS then we risk spamming users - kill the actor
        appLogger.error(s"Killing SQSPoller for queue $SQSName because attempts to delete messages have repeatedly failed.")
        self ! Kill
      }
    }

    Try(SQS.client.deleteMessageBatch(deleteRequest)) match {
      case Success(result) =>
        val failures = result.getFailed.asScala.toList
        if (failures.nonEmpty) {
          appLogger.warn(s"${failures.length} failures while trying to delete from queue $SQSName")
          val failedMessages = failures.flatMap { failure =>
            messages.find(_.getMessageId == failure.getId)
          }
          onFailure(failedMessages)
        }
      case Failure(e) =>
        appLogger.warn(s"Exception trying to delete messages from queue $SQSName: ${e.getMessage}", e)
        onFailure(messages)
    }
  }
}
