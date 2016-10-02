package com.gu.facebook_news_bot.state

import com.gu.facebook_news_bot.models.{MessageFromFacebook, User}
import com.gu.facebook_news_bot.state.Events.Event

//The 'main' state - when we're not expecting any particular message from the user
case object MainState extends State {
  val name = "MAIN"

  def getEvent(user: User, messaging: MessageFromFacebook.Messaging): Event = {
    //Should have either a message or a postback
    val event = messaging.message.fold(messaging.postback.flatMap(processPostback))(processMessage)
    event.getOrElse(Events.unknown)
  }

  private def processMessage(message: MessageFromFacebook.Message): Option[Event] = {
    message.text.map(_.toLowerCase).flatMap { text =>
      if (text.contains("headlines")) Some(Events.headlines)
      else if (text.contains("popular")) Some(Events.headlines)
      else None
    }
  }

  private def processPostback(message: MessageFromFacebook.Postback): Option[Event] = {
    None
  }
}
