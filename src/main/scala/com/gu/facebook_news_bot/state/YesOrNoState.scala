package com.gu.facebook_news_bot.state

import com.gu.facebook_news_bot.models.{MessageFromFacebook, MessageToFacebook, User}
import com.gu.facebook_news_bot.services.{Capi, Facebook}
import com.gu.facebook_news_bot.state.StateHandler.Result

import scala.concurrent.Future

trait YesOrNoState extends State {
  private val YesPattern = "(yes|yeah|yep|sure)".r.unanchored

  def transition(user: User, messaging: MessageFromFacebook.Messaging, capi: Capi, facebook: Facebook): Future[Result] = {
    if (State.getUserInput(messaging).exists(s => YesPattern.findFirstIn(s.toLowerCase).isDefined)) yes(user, facebook)
    else no(user) //if they haven't said yes then it's probably a no
  }

  def question(user: User): Future[Result] = {
    val text = getQuestionText(user)
    val quickReplies = List(
      MessageToFacebook.QuickReply("text", Some("Yes please"), Some("yes")),
      MessageToFacebook.QuickReply("text", Some("No thanks"), Some("no"))
    )
    val response = MessageToFacebook.quickRepliesMessage(user.ID, quickReplies, text)

    Future.successful(State.changeState(user, Name), List(response))
  }

  protected def getQuestionText(user: User): String

  protected def yes(user: User, facebook: Facebook): Future[Result]

  protected def no(user: User): Future[Result]
}
