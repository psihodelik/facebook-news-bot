package com.gu.facebook_news_bot.state

import com.gu.facebook_news_bot.models.{MessageFromFacebook, MessageToFacebook, User}
import com.gu.facebook_news_bot.services.{Capi, Facebook}
import com.gu.facebook_news_bot.state.StateHandler.Result

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object StateHandler {
  def apply(facebook: Facebook, capi: Capi) = new StateHandler(facebook, capi)

  type Result = (User, List[MessageToFacebook])

  val NewUserStateName = "NEW_USER"
}

class StateHandler(facebook: Facebook, capi: Capi) {

  /**
    * @param userOpt  user from dynamodb
    * @param message  message from Facebook messenger
    * @return updated user, plus any messages to send to Facebook
    */
  def process(userOpt: Option[User], message: MessageFromFacebook.Messaging): Future[Result] = {
    userOpt.map(Future.successful).getOrElse(newUser(message.sender.id)) flatMap { user =>
      val state = user.state.map(getStateFromString) getOrElse MainState
      state.transition(user, message, capi, facebook)
    }
  }

  private def newUser(id: String): Future[User] = {
    facebook.getUser(id) map { facebookUser =>
      User(id, localToFront(facebookUser.locale), facebookUser.timezone, "-", "-", Some(StateHandler.NewUserStateName), Some(0))
    }
  }

  private def getStateFromString(state: String): State = state.toUpperCase match {
    case StateHandler.NewUserStateName => SubscribeQuestionState
    case SubscribeQuestionState.Name => SubscribeQuestionState
    case BriefingTimeQuestionState.Name => BriefingTimeQuestionState
    case EditionQuestionState.Name => EditionQuestionState
    case FeedbackState.Name => FeedbackState
    case _ => MainState
  }

  private def localToFront(locale: String): String = {
    locale match {
      case "en_GB" => "uk"
      case "en_US" => "us"
      case "en_UD" => "au"
      case _ => "international"
    }
  }
}
