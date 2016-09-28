package com.gu.facebook_news_bot.state

import com.gu.facebook_news_bot.models.{MessageFromFacebook, User}
import com.gu.facebook_news_bot.state.Events._

trait State {
  def name: String
  /**
    * Decides which event logic to trigger, based on current state + received message
    */
  def getEvent(user: User, message: MessageFromFacebook.Messaging): Event
}
