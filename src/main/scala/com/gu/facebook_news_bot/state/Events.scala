package com.gu.facebook_news_bot.state

import com.gu.contentapi.client.model.v1.Content
import com.gu.facebook_news_bot.models.{MessageFromFacebook, MessageToFacebook, User}
import com.gu.facebook_news_bot.services.{Capi, Facebook}
import com.gu.facebook_news_bot.state.StateHandler.Result

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Event functions define the user's state transition, and build any messages to be sent to user
  */
private[state] object Events {
  type Event = (User, MessageFromFacebook.Messaging, Capi, Facebook) => Future[Result]

  def greeting: Event = (user, message, capi, facebook) => {
    Future.successful((changeState(user, MainState.name), List(MessageToFacebook.textMessage(user.ID, "Hi!"))))
  }

  def unknown: Event = (user, message, capi, facebook) => {
    Future.successful((changeState(user, MainState.name), List(MessageToFacebook.textMessage(user.ID, "???"))))
  }

  def headlines: Event = (user, message, capi, facebook) => {
    capi.getHeadlines(user.front, None) map { contentList =>
      val response = MessageToFacebook(
        recipient = message.sender,
        message = Some(contentToCarousel(contentList))
      )
      (changeState(user, MainState.name), List(response))
    }
  }

  private def changeState(user: User, state: String): User = user.copy(state = state)

  private def contentToCarousel(contentList: Seq[Content]): MessageToFacebook.Message = {
    val tiles = contentList.map { content =>
      MessageToFacebook.Element(
        title = content.webTitle,
        item_url = Some(content.webUrl),
        subtitle = content.fields.flatMap(_.standfirst),
        image_url = None
      )
    }
    val attachment = MessageToFacebook.Attachment(
      `type` = "template",
      payload = MessageToFacebook.Payload(
        template_type = "generic",
        elements = Some(tiles.toList),
        buttons = Some(List(MessageToFacebook.Button(`type` = "element_share")))
      )
    )
    MessageToFacebook.Message(
      attachment = Some(attachment),
      quick_replies = Some(List(MessageToFacebook.QuickReply(
        content_type = "text",
        title = Some("More stories"),
        payload = Some("")
      )))
    )
  }
}
