package com.gu.facebook_news_bot.state

import com.gu.facebook_news_bot.models.{MessageFromFacebook, MessageToFacebook, User}
import com.gu.facebook_news_bot.services.{Capi, Facebook}
import com.gu.facebook_news_bot.state.StateHandler.Result
import com.gu.facebook_news_bot.stores.UserStore
import com.gu.facebook_news_bot.utils.Loggers.LogEvent
import io.circe.generic.auto._

import scala.concurrent.Future

object SearchFeedbackState extends YesOrNoState {
  val Name = "SEARCH_FEEDBACK_QUESTION"

  private case class NoEvent(id: String, event: String = "search_feedback_no", _eventName: String = "search_feedback_no", topic: String) extends LogEvent
  private case class YesEvent(id: String, event: String = "search_feedback_yes", _eventName: String = "search_feedback_yes", topic: String) extends LogEvent

  protected def getQuestionText(user: User) = "Was this helpful?"

  protected def yes(user: User, facebook: Facebook): Future[Result] = {
    State.log(YesEvent(id = user.ID, topic = user.contentTopic.getOrElse("")))
    val message = MessageToFacebook.textMessage(user.ID, "Great!")
    Future.successful(State.changeState(user, MainState.Name), List(message))
  }

  protected def no(user: User): Future[Result] = {
    State.log(NoEvent(id = user.ID, topic = user.contentTopic.getOrElse("")))
    val message = MessageToFacebook.textMessage(user.ID, "Thanks for the feedback.")
    Future.successful(State.changeState(user, MainState.Name), List(message))
  }

  protected override def unrecognised(user: User, messaging: MessageFromFacebook.Messaging, capi: Capi, facebook: Facebook, store: UserStore): Future[Result] =
    MainState.transition(user, messaging, capi, facebook, store)
}
