package com.gu.facebook_news_bot.state

import com.gu.facebook_news_bot.models.{MessageFromFacebook, MessageToFacebook, User}
import com.gu.facebook_news_bot.state.StateHandler.Result

import scala.concurrent.Future

/**
  * Event functions define the user's state transition, and build any messages to be sent to user
  */
private[state] object Events {
  type Event = (User, MessageFromFacebook.Messaging) => Future[Result]

  def greeting: Event = (user: User, message: MessageFromFacebook.Messaging) => {
    Future.successful((user, List(MessageToFacebook.textMessage(user.id, "Hi!"))))
  }
}
