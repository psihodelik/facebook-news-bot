package com.gu.facebook_news_bot.state

import com.gu.facebook_news_bot.models.{MessageFromFacebook, MessageToFacebook, User}
import com.gu.facebook_news_bot.services.{Capi, Facebook}
import com.gu.facebook_news_bot.state.StateHandler.Result
import com.gu.facebook_news_bot.utils.Loggers.LogEvent
import com.gu.facebook_news_bot.utils.ResponseText
import io.circe.generic.auto._

import scala.concurrent.Future

object FeedbackState extends State {
  val Name = "FEEDBACK"

  private case class LogFeedback(id: String, event: String = "feedback", _eventName: String = "feedback", feedback: String) extends LogEvent

  def transition(user: User, messaging: MessageFromFacebook.Messaging, capi: Capi, facebook: Facebook): Future[Result] = {
    val result = for {
      message <- messaging.message
      text <- message.text
    } yield {
      State.log(LogFeedback(id = user.ID, feedback = text))
      Future.successful(State.changeState(user, MainState.Name), List(MessageToFacebook.textMessage(user.ID, "Thank you for the feedback")))
    }

    result.getOrElse(MainState.menu(user, ResponseText.menu))
  }

  def question(user: User): Future[Result] = {
    Future.successful(State.changeState(user, Name), List(MessageToFacebook.textMessage(user.ID, "How can we improve this service? Type here, and I'll pass it onto the Guardian")))
  }
}
