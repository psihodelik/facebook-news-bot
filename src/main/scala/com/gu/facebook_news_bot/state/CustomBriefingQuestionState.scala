package com.gu.facebook_news_bot.state

import com.gu.facebook_news_bot.models.{MessageFromFacebook, MessageToFacebook, User}
import com.gu.facebook_news_bot.services.{Capi, Facebook, Topic}
import com.gu.facebook_news_bot.state.StateHandler.Result
import com.gu.facebook_news_bot.stores.UserStore
import com.gu.facebook_news_bot.utils.Loggers.LogEvent
import com.gu.facebook_news_bot.utils.ResponseText
import io.circe.generic.auto._

import scala.concurrent.Future

case object CustomBriefingQuestionState extends State {

  val Name = "CUSTOM_BRIEFING_QUESTION"

  private val topics = List("tech", "science", "opinion", "sport", "football", "film", "music", "food", "fashion", "travel")
  private val quickReplies: List[MessageToFacebook.QuickReply] = topics.map { topic =>
    MessageToFacebook.QuickReply(title = Some(topic.capitalize), payload = Some(topic))
  }

  private case class NoEvent(id: String, event: String = "custom_briefing_no", _eventName: String = "custom_briefing_no") extends LogEvent
  private case class YesEvent(id: String, event: String = "custom_briefing_yes", _eventName: String = "custom_briefing_yes") extends LogEvent
  private case class UnkownEvent(id: String, event: String = "custom_briefing_unknown", _eventName: String = "custom_briefing_unknown", text: String) extends LogEvent

  def transition(user: User, messaging: MessageFromFacebook.Messaging, capi: Capi, facebook: Facebook, store: UserStore): Future[Result] = {
    State.getUserInput(messaging) match {
      case Some(text) => parseText(user, store, text)
      case None => finish(user)
    }
  }

  def question(user: User, text: Option[String] = None): Future[Result] = {
    val questionMessage = buildQuestionMessage(
      user.ID,
      text.getOrElse("I can personalise your briefing. Do you want to choose a favourite topic?"),
      user.briefingTopic1
    )

    Future.successful(State.changeState(user, Name), List(questionMessage))
  }

  private def parseText(user: User, store: UserStore, text: String): Future[Result] = {
    text.toLowerCase match {
      case State.YesPattern(_) =>
        Future.successful(State.changeState(user, Name), List(buildQuestionMessage(user.ID, "Ok, which topic?", user.briefingTopic1)))

      case State.NoPattern(_) => finish(user)

      case other =>
        Topic.getTopic(other).map { topic =>
          if (user.briefingTopic1.isEmpty) {
            State.log(YesEvent(user.ID))

            val message = buildQuestionMessage(user.ID, "Ok. Do you have another favourite topic?", user.briefingTopic1)
            Future.successful(user.copy(briefingTopic1 = Some(topic.name)), List(message))
          } else {
            MainState.menu(user.copy(briefingTopic2 = Some(topic.name)), ResponseText.subscribed(user.notificationTime))
          }
        } getOrElse {
          State.log(UnkownEvent(user.ID, text = other))
          question(user, Some(s"Sorry, I don't know that topic. Are you interested in any of these?"))
        }
    }
  }

  private def finish(user: User): Future[Result] = {
    if (user.briefingTopic1.isEmpty) State.log(NoEvent(id = user.ID))
    MainState.menu(user, ResponseText.subscribed(user.notificationTime))
  }

  private def buildQuestionMessage(id: String, text: String, excludeTopic: Option[String]): MessageToFacebook = {
    val replies = excludeTopic.map(topic => quickReplies.filterNot(_.payload.contains(topic))).getOrElse(quickReplies)
    MessageToFacebook.quickRepliesMessage(
      id = id,
      quickReplies = replies,
      text = text
    )
  }
}
