package com.gu.facebook_news_bot.state

import com.gu.facebook_news_bot.models.{MessageFromFacebook, MessageToFacebook, User}
import com.gu.facebook_news_bot.services.{Capi, Facebook}
import com.gu.facebook_news_bot.state.StateHandler.Result

import scala.concurrent.Future

object StateHandler {
  def apply(facebook: Facebook, capi: Capi) = new StateHandler(facebook, capi)

  type Result = (User, List[MessageToFacebook])
}

private[state] class StateHandler(facebook: Facebook, capi: Capi) {
  /**
    * @param userOpt  user from dynamodb
    * @param message  message from Facebook messenger
    * @return updated user, plus any messages to send to Facebook
    */
  def process(userOpt: Option[User], message: MessageFromFacebook.Messaging): Future[Result] = {
    val user = userOpt.getOrElse(newUser(message.sender.id))

    val state = getStateFromString(user.state)
    val event = state.getEvent(user, message)
    event(user, message)
  }

  private def newUser(id: String): User = {
    val offset = facebook.getOffset(id)
    User(id, "uk", offset, "-", "-", "NEW_USER")
  }

  private def getStateFromString(state: String): State = state.toUpperCase match {
    case "NEW_USER" => NewUserState
    case _ => MainState
  }
}
