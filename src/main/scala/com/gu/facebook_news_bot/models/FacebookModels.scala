package com.gu.facebook_news_bot.models

case class Id(id: String)

/**
  * Messages sent to Messenger users
  */
case class MessageToFacebook(recipient: Id,
                             message: Option[MessageToFacebook.Message] = None,
                             sender_action: Option[String] = None,
                             notification_type: Option[String] = None)
object MessageToFacebook {

  def textMessage(id: String, message: String) =
    MessageToFacebook(recipient = Id(id), Some(Message(text = Some(message))))

  def errorMessage(id: String) = textMessage(id, "Sorry, I'm having some technical difficulties at the moment. Please try again later.")

  def buttonsMessage(id: String, buttons: Seq[Button], text: String): MessageToFacebook = {
    val attachment = MessageToFacebook.Attachment.buttonsAttachment(buttons, text)
    MessageToFacebook(
      recipient = Id(id),
      message = Some(MessageToFacebook.Message(
        attachment = Some(attachment)
      ))
    )
  }

  private val MaxQuickReplies = 10
  def quickRepliesMessage(id: String, quickReplies: Seq[QuickReply], text: String): MessageToFacebook = {
    val message = MessageToFacebook.Message(
      text = Some(text),
      quick_replies = Some(quickReplies.take(MaxQuickReplies))
    )
    MessageToFacebook(
      recipient = Id(id),
      message = Some(message)
    )
  }

  case class Message(text: Option[String] = None,
                     attachment: Option[Attachment] = None,
                     quick_replies: Option[Seq[QuickReply]] = None,
                     metadata: Option[String] = None)

  case class Attachment(`type`: String, payload: Payload)
  object Attachment {
    def genericAttachment(elements: Seq[Element]): Attachment = Attachment(
      `type` = "template",
      payload = Payload(
        template_type = "generic",
        elements = Some(elements.toList)
      )
    )

    def buttonsAttachment(buttons: Seq[Button], text: String): Attachment = Attachment(
      `type` = "template",
      payload = MessageToFacebook.Payload(
        template_type = "button",
        text = Some(text),
        buttons = Some(buttons)
      )
    )

    // the list attachment differs in style depending on whether prominence is given to top element
    def plainListAttachment(elements: Seq[Element]): Attachment = Attachment(
      `type` = "template",
      payload = Payload(
        template_type = "list",
        top_element_style = Some("compact"),
        elements = Some(elements.toList)
      )
    )
  }

  case class QuickReply(content_type: String = "text",
                        title: Option[String] = None,
                        payload: Option[String] = None,
                        image_url: Option[String] = None)

  case class Payload(template_type: String, text: Option[String] = None, elements: Option[Seq[Element]] = None, buttons: Option[Seq[Button]] = None, top_element_style: Option[String] = None)
  case class Element(title: String, item_url: Option[String] = None, image_url: Option[String] = None, subtitle: Option[String] = None, buttons: Option[Seq[Button]] = None)
  case class Button(`type`: String, title: Option[String] = None, url: Option[String] = None, payload: Option[String] = None)
}

/**
  * Messages received from Messenger users
  */
case class MessageFromFacebook(entry: Seq[MessageFromFacebook.Entry])
object MessageFromFacebook {

  case class Entry(id: String, time: Long, messaging: Seq[Messaging])

  case class Messaging(sender: Id,
                       recipient: Id,
                       timestamp: Long,
                       message: Option[Message],
                       postback: Option[Postback],
                       referral: Option[Referral])

  case class Message(mid: String,
                     seq: Int,
                     text: Option[String],
                     quick_reply: Option[QuickReply])

  case class Payload(url: Option[String])

  //quick_reply and postback payloads contain the string that we supplied - this is different from the attachment payloads.
  case class QuickReply(payload: String)

  case class Postback(payload: String, referral: Option[Referral])

  case class Referral(ref: String, source: String, `type`: String)

}

case class FacebookUser(locale: String, timezone: Double)
