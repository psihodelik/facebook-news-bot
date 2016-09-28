package com.gu.facebook_news_bot.state

import com.gu.facebook_news_bot.models.{MessageFromFacebook, User}
import com.gu.facebook_news_bot.state.Events.Event

//The 'main' state - when we're not expecting any particular message from the user
case object MainState extends State {
  def name = "MAIN"

  def getEvent(user: User, message: MessageFromFacebook.Messaging): Event = {
    //TODO - handle many different kinds of message
    Events.greeting
  }
}
