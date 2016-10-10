package com.gu.facebook_news_bot.state

import com.gu.facebook_news_bot.models.{MessageFromFacebook, MessageToFacebook, User}
import com.gu.facebook_news_bot.services.{Capi, Facebook}
import com.gu.facebook_news_bot.state.StateHandler.Result
import com.gu.facebook_news_bot.utils.ResponseText

import scala.concurrent.Future

trait State {
  val name: String
  /**
    * Define the user's state transition, and build any messages to be sent to user
    */
  def transition(user: User, message: MessageFromFacebook.Messaging, capi: Capi, facebook: Facebook): Future[Result]
}

object State {
  def changeState(user: User, state: String): User = user.copy(state = Some(state))

  def greeting(user: User): Future[Result] = {
    Future.successful((changeState(user, MainState.name), List(MessageToFacebook.textMessage(user.ID, ResponseText.greeting))))
  }

  def unknown(user: User): Future[Result] = {
    Future.successful((changeState(user, MainState.name), List(MessageToFacebook.textMessage(user.ID, ResponseText.unknown))))
  }
}
