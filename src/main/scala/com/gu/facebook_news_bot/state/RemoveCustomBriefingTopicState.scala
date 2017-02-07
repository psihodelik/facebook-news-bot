package com.gu.facebook_news_bot.state

import com.gu.facebook_news_bot.models.{MessageFromFacebook, MessageToFacebook, User}
import com.gu.facebook_news_bot.services.{Capi, Facebook}
import com.gu.facebook_news_bot.state.StateHandler._
import com.gu.facebook_news_bot.stores.UserStore

import scala.concurrent.Future

object RemoveCustomBriefingTopicState extends State {
  val Name = "REMOVE_CUSTOM_BRIEFING_TOPIC"

  def transition(user: User, messaging: MessageFromFacebook.Messaging, capi: Capi, facebook: Facebook, store: UserStore): Future[Result] = {
    State.getUserInput(messaging).flatMap { text =>
      val maybeUpdatedUser = updateUserTopics(user, text.toLowerCase)
      maybeUpdatedUser.map(updatedUser => Future.successful((updatedUser, List(buildMessage(updatedUser.ID, text)))))

    } getOrElse State.unknown(user)
  }

  def question(user: User): Future[Result] = {
    val quickReplies = List(user.briefingTopic1, user.briefingTopic2)
      .flatten
      .map(topic => MessageToFacebook.QuickReply(title = Some(topic.capitalize), payload = Some(topic)))

    val message = MessageToFacebook.quickRepliesMessage(
      user.ID,
      quickReplies = quickReplies,
      text = "Which topic do you want to remove from your morning briefing?"
    )
    Future.successful(State.changeState(user, Name), List(message))
  }

  private def updateUserTopics(user: User, topic: String): Option[User] = {
    if (user.briefingTopic1.contains(topic)) {
      val updatedUser = user.copy(
        state = Some(MainState.Name),
        briefingTopic1 = user.briefingTopic2,
        briefingTopic2 = None
      )
      Some(updatedUser)

    } else if (user.briefingTopic2.contains(topic)) {
      val updatedUser = user.copy(
        state = Some(MainState.Name),
        briefingTopic2 = None
      )
      Some(updatedUser)

    } else None
  }

  private def buildMessage(id: String, topic: String) = MessageToFacebook.textMessage(id, s"I've removed $topic from your morning briefing.")
}
