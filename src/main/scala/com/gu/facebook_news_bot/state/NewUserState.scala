package com.gu.facebook_news_bot.state

import com.gu.facebook_news_bot.models.{MessageFromFacebook, User}
import com.gu.facebook_news_bot.state.Events.Event

case object NewUserState extends State {
  def name = "NEW_USER"

  def getEvent(user: User, message: MessageFromFacebook.Messaging): Event = {
    Events.greeting
  }
}
