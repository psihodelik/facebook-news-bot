package com.gu.facebook_news_bot.state.football

import com.gu.facebook_news_bot.models.{MessageFromFacebook, User}
import com.gu.facebook_news_bot.services.{Capi, Facebook}
import com.gu.facebook_news_bot.state.State
import com.gu.facebook_news_bot.state.StateHandler._

import scala.concurrent.Future

case object FootballTransferSubscribeQuestionState extends State {
  val Name = "FOOTBALL_TRANSFER_SUBSCRIBE_QUESTION"

  def transition(user: User, messaging: MessageFromFacebook.Messaging, capi: Capi, facebook: Facebook): Future[Result] = {
    Future.successful((user, Nil))
  }

  def question(user: User): Future[Result] = {
    Future.successful((user, Nil))
  }
}
