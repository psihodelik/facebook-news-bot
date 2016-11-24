package com.gu.facebook_news_bot.state

import com.gu.facebook_news_bot.models.{Id, MessageFromFacebook, MessageToFacebook, User}
import com.gu.facebook_news_bot.services.{Capi, Facebook}
import com.gu.facebook_news_bot.state.StateHandler.Result
import com.gu.facebook_news_bot.utils.Loggers.LogEvent
import com.gu.facebook_news_bot.utils.ResponseText
import io.circe.generic.auto._

import scala.concurrent.Future

/**
  * When a user asks to update their edition they enter this state.
  * It lists the possible editions.
  * Upon receiving a valid edition, the 'front' field is updated in dynamo and the user enters the MAIN state.
  */
case object EditionQuestionState extends State {
  val Name = "EDITION_QUESTION"

  val Editions = Seq("au", "uk", "us", "international")

  private case class EditionEvent(id: String, event: String = "change_edition", _eventName: String = "change_edition", edition: String) extends LogEvent

  def transition(user: User, messaging: MessageFromFacebook.Messaging, capi: Capi, facebook: Facebook): Future[Result] = {
    messaging.postback.map(MainState.onMenuButtonClick(user, _, capi, facebook)) getOrElse {
      if (user.state.contains(Name)) {
        //There should be valid edition in either the text or quick_reply fields
        val maybeEd = Editions.find(ed => State.getUserInput(messaging).map(_.toLowerCase).exists(u => u.contains(ed)))
        maybeEd.map(ed => success(user, ed)).getOrElse(question(user))
      } else {
        //New state
        question(user)
      }
    }
  }

  def question(user: User): Future[Result] = {
    val message = MessageToFacebook.Message(
      text = Some(ResponseText.editionQuestion),
      quick_replies = Some(List(
        MessageToFacebook.QuickReply("text", Some("UK"), Some("uk")),
        MessageToFacebook.QuickReply("text", Some("US"), Some("us")),
        MessageToFacebook.QuickReply("text", Some("Australian"), Some("au")),
        MessageToFacebook.QuickReply("text", Some("International"), Some("international"))
      ))
    )
    val response = MessageToFacebook(
      recipient = Id(user.ID),
      message = Some(message)
    )
    Future.successful(State.changeState(user, Name), List(response))
  }

  def frontToUserFriendly(front: String): String = {
    front match {
      case "au" => "Australia"
      case "international" => "International"
      case s => s.toUpperCase
    }
  }

  private def success(user: User, edition: String): Future[Result] = {
    log(EditionEvent(id = user.ID, edition = edition))
    val response = MessageToFacebook.textMessage(user.ID, ResponseText.editionChanged(frontToUserFriendly(edition)))
    val updatedUser = user.copy(state = Some(MainState.Name), front = edition)
    Future.successful((updatedUser, List(response)))
  }
}
