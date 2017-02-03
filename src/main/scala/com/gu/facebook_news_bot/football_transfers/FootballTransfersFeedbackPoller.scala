package com.gu.facebook_news_bot.football_transfers

import akka.actor.Props
import com.gu.facebook_news_bot.BotConfig
import com.gu.facebook_news_bot.services.Facebook.FacebookMessageResult
import com.gu.facebook_news_bot.services.{Capi, Facebook, SQSMessageBody}
import com.gu.facebook_news_bot.state.FootballTransferStates
import com.gu.facebook_news_bot.stores.UserStore
import com.gu.facebook_news_bot.utils.{JsonHelpers, Notifier, SQSPoller}
import io.circe.generic.auto._

import scala.concurrent.duration._
import scala.concurrent.Future

object FootballTransfersFeedbackPoller {
  def props(facebook: Facebook, capi: Capi, userStore: UserStore) = Props(new FootballTransfersFeedbackPoller(facebook, capi, userStore))
}

class FootballTransfersFeedbackPoller(val facebook: Facebook, val capi: Capi, val userStore: UserStore) extends SQSPoller {
  val SQSName = BotConfig.football.rumoursSQSName //Use existing infrastructure for this one-off message
  override val PollPeriod = 2000.millis

  protected def process(messageBody: SQSMessageBody): Future[List[FacebookMessageResult]] = {
    JsonHelpers.decodeJson[UserFootballTransferRumour](messageBody.Message).map { rumour =>
      val futureMaybeUser = for {
        maybeUser <- Notifier.getUser(rumour.userId, facebook, userStore)
      } yield maybeUser

      futureMaybeUser.flatMap {
        case Some(user) =>
          FootballTransferStates.FootballTransfersFeedbackState.question(user) flatMap {
            case (updatedUser, messages) => Notifier.sendAndUpdate(updatedUser, messages, facebook, userStore)
          }
        case None => Future.successful(Nil)
      }
    }.getOrElse(Future.successful(Nil))
  }
}
