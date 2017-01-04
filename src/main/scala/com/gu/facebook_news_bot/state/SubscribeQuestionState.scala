package com.gu.facebook_news_bot.state

import com.gu.facebook_news_bot.models.{MessageToFacebook, User}
import com.gu.facebook_news_bot.services.Facebook
import com.gu.facebook_news_bot.state.StateHandler.Result
import com.gu.facebook_news_bot.utils.Loggers.LogEvent
import com.gu.facebook_news_bot.utils.ResponseText
import io.circe.generic.auto._

import scala.concurrent.Future

/**
  * The user enters this state if they are new, or if they click 'Manage subscription' in the menu and are not subscribed.
  * If the user says yes, they enter the BRIEFING_TIME_QUESTION state. Otherwise they enter the MAIN state.
  */
case object SubscribeQuestionState extends YesOrNoState {
  val Name = "SUBSCRIBE_QUESTION"

  private case class SubscribeNoEvent(id: String, event: String = "subscribe_no", _eventName: String = "subscribe_no") extends LogEvent
  private case class SubscribeYesEvent(id: String, event: String = "subscribe_yes", _eventName: String = "subscribe_yes") extends LogEvent

  protected def getQuestionText(user: User): String =
    if (user.state.contains(StateHandler.NewUserStateName)) ResponseText.welcome else ResponseText.subscribeQuestion

  protected def yes(user: User, facebook: Facebook): Future[Result] = {
    State.log(SubscribeYesEvent(user.ID))
    EditionQuestionState.question(user)
  }

  protected def no(user: User): Future[Result] = {
    State.log(SubscribeNoEvent(user.ID))
    val response = MessageToFacebook.textMessage(user.ID, ResponseText.subscribeNo)
    Future.successful(State.changeState(user, MainState.Name), List(response))
  }
}
