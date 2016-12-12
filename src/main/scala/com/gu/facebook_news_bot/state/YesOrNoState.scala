package com.gu.facebook_news_bot.state

import com.gu.facebook_news_bot.models.{MessageFromFacebook, MessageToFacebook, User}
import com.gu.facebook_news_bot.services.{Capi, Facebook}
import com.gu.facebook_news_bot.state.StateHandler.Result
import com.gu.facebook_news_bot.stores.UserStore

import scala.concurrent.Future

object YesOrNoState {
  val YesPattern = """\b(yes|yeah|yep|sure|ok|okay)\b""".r.unanchored
}

trait YesOrNoState extends State {
  def transition(user: User, messaging: MessageFromFacebook.Messaging, capi: Capi, facebook: Facebook, store: UserStore): Future[Result] = {
    if (State.getUserInput(messaging).exists(s => YesOrNoState.YesPattern.findFirstIn(s.toLowerCase).isDefined)) yes(user, facebook)
    else no(user)
  }

  def question(user: User, text: Option[String] = None): Future[Result] = {
    val quickReplies = List(
      MessageToFacebook.QuickReply("text", Some("Yes"), Some("yes")),
      MessageToFacebook.QuickReply("text", Some("No"), Some("no"))
    )
    val response = MessageToFacebook.quickRepliesMessage(user.ID, quickReplies, text.getOrElse(getQuestionText(user)))

    Future.successful(State.changeState(user, Name), List(response))
  }

  protected def getQuestionText(user: User): String

  protected def yes(user: User, facebook: Facebook): Future[Result]

  protected def no(user: User): Future[Result]
}
