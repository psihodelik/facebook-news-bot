package com.gu.facebook_news_bot.state

import com.gu.facebook_news_bot.models.{Id, MessageFromFacebook, MessageToFacebook, User}
import com.gu.facebook_news_bot.services.{Capi, Facebook, Topic}
import com.gu.facebook_news_bot.state.StateHandler.Result
import com.gu.facebook_news_bot.utils.ResponseText
import com.gu.facebook_news_bot.utils.FacebookMessageBuilder.{ contentToCarousel, CarouselSize }

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

//The 'main' state - when we're not expecting any particular message from the user
case object MainState extends State {
  val name = "MAIN"

  private object Patterns {
    val headlines = """(^|\W)headlines($|\W)""".r.unanchored
    val popular = """(^|\W)popular($|\W)""".r.unanchored
    val more = """(^|\W)more($|\W)""".r.unanchored
    val greeting = """^(hi|hello|ola|hey|salut)\s*[!?.]*$""".r
  }

  private sealed trait Event
  private case class NewContentEvent(contentType: Option[ContentType], topic: Option[Topic] = None) extends Event
  private case object MoreContentEvent extends Event
  private case object GreetingEvent extends Event

  private sealed trait ContentType { val name: String }
  private case object HeadlinesType extends ContentType { val name = "headlines" }
  private case object MostViewedType extends ContentType { val name = "most_viewed" }
  private object ContentType {
    def fromString(s: String): Option[ContentType] = s match {
      case HeadlinesType.name => Some(HeadlinesType)
      case MostViewedType.name => Some(MostViewedType)
      case _ => None
    }
  }

  def transition(user: User, messaging: MessageFromFacebook.Messaging, capi: Capi, facebook: Facebook): Future[Result] = {
    //Should have either a message or a postback
    messaging.message.fold(messaging.postback.flatMap(processPostback))(processMessage) flatMap {

      case NewContentEvent(maybeContentType, maybeTopic) =>
        //Either have a new contentType, or use an existing contentType
        maybeContentType.orElse(user.contentType.flatMap(ContentType.fromString)) map { contentType =>
          carousel(user, contentType, maybeTopic, 0, capi)
        }

      case MoreContentEvent =>
        //Must have contentType in User
        user.contentType.flatMap(ContentType.fromString).map { contentType =>
          carousel(
            user = user,
            contentType = contentType,
            topic = user.contentTopic.flatMap(Topic.getTopic),
            offset = user.contentOffset.getOrElse(0) + CarouselSize,
            capi = capi)
        }

      case GreetingEvent => Some(State.greeting(user))

    } getOrElse State.unknown(user)
  }

  //A Message must have a text field, but may also have a quick_reply
  private def processMessage(message: MessageFromFacebook.Message): Option[Event] =
    message.quick_reply.flatMap(reply => processText(reply.payload)).orElse(processText(message.text))

  //Button click
  private def processPostback(message: MessageFromFacebook.Postback): Option[Event] = {
    None  //TODO - handle button clicks
  }

  private def processText(text: String): Option[Event] = {
    //Note - each case must reference all capture groups from the regex for the extractor to work
    text.toLowerCase match {
      case Patterns.more(_,_) => Some(MoreContentEvent)
      case Patterns.headlines(_,_) => Some(NewContentEvent(contentType = Some(HeadlinesType)))
      case Patterns.popular(_,_) => Some(NewContentEvent(contentType = Some(MostViewedType)))
      case Patterns.greeting(_) => Some(GreetingEvent)
      case _ => None  //TODO - handle topics
    }
  }

  private def carousel(user: User, contentType: ContentType, topic: Option[Topic], offset: Int, capi: Capi): Future[Result] = {
    val futureCarousel = contentType match {
      case MostViewedType => capi.getMostViewed(user.front, topic) map (contentToCarousel(_, offset))
      case HeadlinesType => capi.getHeadlines(user.front, topic) map (contentToCarousel(_, offset))
    }

    futureCarousel map {
      case Some(carousel) =>
        val updatedUser = user.copy(
          contentType = Some(contentType.name),
          contentTopic = topic.map(_.name),
          contentOffset = Some(offset)
        )

        val response = MessageToFacebook(
          recipient = Id(user.ID),
          message = Some(carousel)
        )
        (updatedUser, List(response))

      case None => (user, List(MessageToFacebook.textMessage(user.ID, ResponseText.noResults)))
    }
  }
}
