package com.gu.facebook_news_bot.utils

import java.util.concurrent.Executors

import akka.actor.Actor
import com.amazonaws.services.sqs.model.{DeleteMessageBatchRequest, DeleteMessageBatchRequestEntry, Message, ReceiveMessageRequest}
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.gu.facebook_news_bot.services.Facebook.FacebookMessageResult
import com.gu.facebook_news_bot.services._
import com.gu.facebook_news_bot.utils.Loggers._
import com.gu.facebook_news_bot.utils.SQSPoller.Poll
import io.circe.generic.auto._

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

object SQSPoller {
  case object Poll
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
        Future.sequence(decodedMessages.map(process)).foreach { result =>
          //Resume polling only once the requests have been sent
          self ! Poll
        }
      } else context.system.scheduler.scheduleOnce(PollPeriod, self, Poll)

      //Delete items from the queue
      if (messages.nonEmpty) {
        val deleteRequest = new DeleteMessageBatchRequest(
          SQSName,
          messages.map(message => new DeleteMessageBatchRequestEntry(message.getMessageId, message.getReceiptHandle)).asJava
        )
        Try(SQS.client.deleteMessageBatch(deleteRequest)) recover {
          case e => appLogger.warn(s"Failed to delete messages from queue: ${e.getMessage}", e)
        }
      }

    case _ => appLogger.warn("Unknown message received by SQS Poller")
  }

  protected def process(messageBody: SQSMessageBody): Future[List[FacebookMessageResult]]
}
