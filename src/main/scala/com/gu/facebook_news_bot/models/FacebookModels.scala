package com.gu.facebook_news_bot.models

case class Id(id: String) extends AnyVal

/**
  * Messages sent to Messenger users
  */
case class MessageToFacebook(recipient: Id,
                             message: Option[MessageToFacebook.Message],
                             sender_action: Option[String],
                             notification_type: Option[String])
object MessageToFacebook {

  def textMessage(id: String, message: String) =
    MessageToFacebook(Id(id),
     Some(Message(Some(message),None,None,None)),
     None, None)

  def errorMessage(id: String) = textMessage(id, "Sorry, I'm having some technical difficulties at the moment. Please try again later.")

  case class Message(text: Option[String],
                     attachment: Option[Attachment],
                     quick_replies: Option[List[QuickReply]],
                     metadata: Option[String])

  case class Attachment(`type`: String, payload: String)

  case class QuickReply(content_type: String,
                        title: Option[String],
                        payload: Option[String],
                        image_url: Option[String])

}

/**
  * Messages received from Messenger users
  */
case class MessageFromFacebook(entry: List[MessageFromFacebook.Entry])
object MessageFromFacebook {

  case class Entry(id: String, time: Long, messaging: List[Messaging])

  case class Messaging(sender: Id,
                       recipient: Id,
                       timestamp: Long,
                       message: Option[Message],
                       postback: Option[Postback])

  case class Message(mid: String,
                     seq: Int,
                     text: Option[String],
                     attachments: Option[List[Attachment]],
                     quick_reply: Option[QuickReply])

  case class Attachment(`type`: String, payload: Payload)

  case class Payload(url: Option[String])

  //quick_reply and postback payloads contain the string that we supplied - this is different from the attachment payloads.
  case class QuickReply(payload: String) extends AnyVal

  case class Postback(payload: String) extends AnyVal

}

case class FacebookUser(first_name: String, last_name: String, gender: String, locale: String, timezone: Double)
