package com.gu.facebook_news_bot.state

import com.gu.facebook_news_bot.models.{MessageFromFacebook, User}
import com.gu.facebook_news_bot.services.{Capi, Facebook}
import com.gu.facebook_news_bot.state.StateHandler.Result

import scala.concurrent.Future

case object NewUserState extends State {
  val name = "NEW_USER"

  def transition(user: User, messaging: MessageFromFacebook.Messaging, capi: Capi, facebook: Facebook): Future[Result] = {
    State.greeting(user)
  }
}
