package com.gu.facebook_news_bot.state

import com.gu.facebook_news_bot.models.{MessageFromFacebook, MessageToFacebook, User}
import com.gu.facebook_news_bot.services.{Capi, Facebook}
import com.gu.facebook_news_bot.state.StateHandler.Result
import com.gu.facebook_news_bot.stores.UserStore
import com.gu.facebook_news_bot.utils.Loggers.LogEvent
import com.gu.facebook_news_bot.utils.ResponseText
import io.circe.generic.auto._

import scala.concurrent.Future

object ManageMorningBriefingState extends State {
  val Name = "MANAGE_MORNING_BRIEFING"

  private case class UnsubscribeLogEvent(id: String, event: String = "unsubscribe", _eventName: String = "unsubscribe") extends LogEvent

  def transition(user: User, messaging: MessageFromFacebook.Messaging, capi: Capi, facebook: Facebook, store: UserStore): Future[Result] = {
    State.getUserInput(messaging).flatMap { text =>
      val lower = text.toLowerCase
      if (lower.contains("time")) Some(BriefingTimeQuestionState.question(user))
      else if (lower.contains("edition")) Some(EditionQuestionState.question(user))
      else if (lower.contains("unsubscribe")) Some(unsubscribe(user))
      else if (lower.contains("add")) Some(CustomBriefingQuestionState.question(user))
      else if (lower.contains("remove")) Some(RemoveCustomBriefingTopicState.question(user))
      else None
    } getOrElse State.unknown(user)
  }

  def question(user: User): Future[Result] = {
    if (user.notificationTime == "-") {
      SubscribeQuestionState.question(user)
    } else {
      val quickReplies = Seq(
        MessageToFacebook.QuickReply(title = Some("Change time"), payload = Some("time")),
        MessageToFacebook.QuickReply(title = Some("Change edition"), payload = Some("edition")),
        MessageToFacebook.QuickReply(title = Some("Unsubscribe"), payload = Some("unsubscribe"))
      )

      val topicQuickReplies = List(
        user.briefingTopic1.map( _ => MessageToFacebook.QuickReply(title = Some("Remove topic"), payload = Some("remove"))),
        if (user.briefingTopic2.isEmpty) Some(MessageToFacebook.QuickReply(title = Some("Add topic"), payload = Some("add"))) else None
      ).flatten

      val message = MessageToFacebook.quickRepliesMessage(
        user.ID,
        quickReplies ++ topicQuickReplies,
        ResponseText.manageSubscription(EditionQuestionState.frontToUserFriendly(user.front), user.notificationTime)
      )

      Future.successful((State.changeState(user, Name), List(message)))
    }
  }

  def unsubscribe(user: User): Future[Result] = {
    State.log(UnsubscribeLogEvent(user.ID))

    val updatedUser = user.copy(
      state = Some(MainState.Name),
      notificationTime = "-",
      notificationTimeUTC = "-",
      briefingTopic1 = None,
      briefingTopic2 = None
    )
    val response = MessageToFacebook.textMessage(user.ID, ResponseText.unsubscribe)
    Future.successful((updatedUser, List(response)))
  }
}
