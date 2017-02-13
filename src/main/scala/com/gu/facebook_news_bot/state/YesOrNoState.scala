package com.gu.facebook_news_bot.state

import com.gu.facebook_news_bot.models.{MessageFromFacebook, MessageToFacebook, User}
import com.gu.facebook_news_bot.services.{Capi, Facebook}
import com.gu.facebook_news_bot.state.StateHandler.Result
import com.gu.facebook_news_bot.stores.UserStore

import scala.concurrent.Future

trait YesOrNoState extends State {
  def transition(user: User, messaging: MessageFromFacebook.Messaging, capi: Capi, facebook: Facebook, store: UserStore): Future[Result] = {
    State.getUserInput(messaging).map(_.toLowerCase) match {
      case Some(text) =>
        if (State.YesPattern.findFirstIn(text).isDefined) yes(user, facebook)
        else if (State.NoPattern.findFirstIn(text).isDefined) no(user)
        else unrecognised(user, messaging, capi, facebook, store)

      case None => unrecognised(user, messaging, capi, facebook, store)
    }
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

  //By default, if they haven't said yes then it's a no
  protected def unrecognised(user: User, messaging: MessageFromFacebook.Messaging, capi: Capi, facebook: Facebook, store: UserStore): Future[Result] = no(user)
}
