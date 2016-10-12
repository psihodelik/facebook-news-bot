package com.gu.facebook_news_bot.state

import com.gu.facebook_news_bot.models.{MessageFromFacebook, MessageToFacebook, User}
import com.gu.facebook_news_bot.services.{Capi, Facebook}
import com.gu.facebook_news_bot.state.StateHandler.Result
import com.gu.facebook_news_bot.utils.ResponseText

import scala.concurrent.Future

/**
  * The user enters this state if they are new, or if they click 'Manage subscription' in the menu and are not subscribed.
  * If the user says yes, they enter the BRIEFING_TIME_QUESTION state. Otherwise they enter the MAIN state.
  */
case object SubscribeQuestionState extends State {
  val Name = "SUBSCRIBE_QUESTION"

  val YesPattern = "(yes|yeah|yep|sure)".r.unanchored

  def transition(user: User, messaging: MessageFromFacebook.Messaging, capi: Capi, facebook: Facebook): Future[Result] = {
    messaging.postback.map(processPostback(user, _, capi, facebook)) getOrElse {
      if (user.state.contains(Name)) {
        //Should be yes or no
        if (State.getUserInput(messaging).exists(s => YesPattern.findFirstIn(s).isDefined)) yes(user, facebook)
        else no(user) //if they haven't said yes then it's probably a no

      } else {
        //In case a new user arrives here without the 'start' postback event
        question(user)
      }
    }
  }

  def question(user: User): Future[Result] = {
    val text = if (user.state.contains(StateHandler.NewUserStateName)) ResponseText.welcome else ResponseText.subscribeQuestion
    val quickReplies = List(
      MessageToFacebook.QuickReply("text", Some("Yes please"), Some("yes")),
      MessageToFacebook.QuickReply("text", Some("No thanks"), Some("no"))
    )
    val response = MessageToFacebook.quickRepliesMessage(user.ID, quickReplies, text)

    Future.successful(State.changeState(user, Name), List(response))
  }

  private def yes(user: User, facebook: Facebook): Future[Result] = {
    BriefingTimeQuestionState.question(user)
  }

  private def no(user: User): Future[Result] = {
    val response = MessageToFacebook.textMessage(user.ID, ResponseText.subscribeNo)
    Future.successful(State.changeState(user, MainState.Name), List(response))
  }

  private def processPostback(user: User, postback: MessageFromFacebook.Postback, capi: Capi, facebook: Facebook): Future[Result] = {
    if (postback.payload.toLowerCase().contains("start")) question(user)  //new user
    else MainState.onMenuButtonClick(user, postback, capi, facebook)
  }
}
