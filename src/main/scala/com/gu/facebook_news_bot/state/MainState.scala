package com.gu.facebook_news_bot.state

import com.gu.facebook_news_bot.models.{Id, MessageFromFacebook, MessageToFacebook, User}
import com.gu.facebook_news_bot.services.{Capi, Facebook, Topic}
import com.gu.facebook_news_bot.state.StateHandler.Result
import com.gu.facebook_news_bot.utils.ResponseText
import com.gu.facebook_news_bot.utils.FacebookMessageBuilder.{CarouselSize, contentToCarousel}
import io.circe.generic.auto._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

//The 'main' state - when we're not expecting any particular message from the user
case object MainState extends State {
  val Name = "MAIN"

  private case class ContentLogEvent(id: String, event: String, topic: String, offset: Int) extends LogEvent
  private case class UnsubscribeLogEvent(id: String, event: String = "unsubscribe") extends LogEvent

  private object Patterns {
    val headlines = """(^|\W)headlines($|\W)""".r.unanchored
    val popular = """(^|\W)popular($|\W)""".r.unanchored
    val more = """(^|\W)more($|\W)""".r.unanchored
    val greeting = """^(hi|hello|ola|hey|salut)\s*[!?.]*$""".r
    val menu = """(^|\W)menu($|\W)""".r.unanchored
    val help = """(^|\W)help($|\W)""".r.unanchored
    val subscribe = """(^|\W)subscribe($|\W)""".r.unanchored
    val unsubscribe = """(^|\W)unsubscribe($|\W)""".r.unanchored
  }

  private sealed trait Event
  private case class NewContentEvent(contentType: Option[ContentType], topic: Option[Topic] = None) extends Event
  private case object MoreContentEvent extends Event
  private case object GreetingEvent extends Event
  private case class MenuEvent(text: String) extends Event
  private case object ManageSubscriptionEvent extends Event
  private case object SubscribeYesEvent extends Event
  private case object ChangeFrontEvent extends Event
  private case object UnsubscribeEvent extends Event

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
    messaging.message.fold(messaging.postback.flatMap(processButtonPostback))(processMessage).map { event =>
      processEvent(user, event, capi, facebook)
    } getOrElse State.unknown(user)
  }

  //Clicking a menu button brings the user into the MAIN state - other states can call this after receiving a postback
  def onMenuButtonClick(user: User, postback: MessageFromFacebook.Postback, capi: Capi, facebook: Facebook): Future[Result] = {
    val result = processButtonPostback(postback) map { event =>
      processEvent(user, event, capi, facebook)
    }
    result getOrElse State.unknown(user)
  }

  //For use by morning briefing, which for now just uses editors-picks and leaves the user in the MAIN state
  def getHeadlines(user: User, capi: Capi, variant: Option[String] = None): Future[Result] = carousel(user, HeadlinesType, None, 0, capi, variant)

  private def processEvent(user: User, event: Event, capi: Capi, facebook: Facebook): Future[Result] = {
    val result = event match {
      case NewContentEvent(maybeContentType, maybeTopic) =>
        //Either have a new contentType, or use an existing contentType
        maybeContentType.orElse(user.contentType.flatMap(ContentType.fromString)) map { contentType =>
          log(ContentLogEvent(user.ID, contentType.name, maybeTopic.flatMap(_.terms.headOption).getOrElse(""), 0))

          carousel(user, contentType, maybeTopic, 0, capi)
        }

      case MoreContentEvent =>
        //Must have contentType in User
        user.contentType.flatMap(ContentType.fromString).map { contentType =>
          val offset = user.contentOffset.getOrElse(0) + CarouselSize

          log(ContentLogEvent(user.ID, contentType.name, user.contentTopic.getOrElse(""), offset))

          carousel(
            user = user,
            contentType = contentType,
            topic = user.contentTopic.flatMap(Topic.getTopic),
            offset = offset,
            capi = capi)
        }

      case GreetingEvent => Some(State.greeting(user))
      case MenuEvent(text) => Some(menu(user, text))
      case ManageSubscriptionEvent => Some(manageSubscription(user))
      case SubscribeYesEvent => Some(BriefingTimeQuestionState.question(user))
      case UnsubscribeEvent => Some(unsubscribe(user))
      case ChangeFrontEvent => Some(EditionQuestionState.question(user))
    }

    result.getOrElse(State.unknown(user))
  }

  //A Message must have a text field, but may also have a quick_reply
  private def processMessage(message: MessageFromFacebook.Message): Option[Event] =
    message.quick_reply.flatMap(reply => processText(reply.payload)).orElse(processText(message.text))

  //On button click
  private def processButtonPostback(postback: MessageFromFacebook.Postback): Option[Event] = {
    //Use contains for backwards compatibility - this can be replaced with pattern matching later
    if (postback.payload.contains("headlines")) Some(NewContentEvent(Some(HeadlinesType)))
    else if (postback.payload.contains("most_popular")) Some(NewContentEvent(Some(MostViewedType)))
    else if (postback.payload.contains("manage_subscription")) Some(ManageSubscriptionEvent)
    else if (postback.payload.contains("subscribe_yes")) Some(SubscribeYesEvent)
    else if (postback.payload.contains("change_front_menu")) Some(ChangeFrontEvent)
    else if (postback.payload.contains("unsubscribe")) Some(UnsubscribeEvent)
    else None
  }

  private def processText(raw: String): Option[Event] = {
    //Note - each case must reference all capture groups from the regex for the extractor to work
    val text = raw.toLowerCase
    val event = text match {
      case Patterns.more(_,_) => Some(MoreContentEvent)
      case Patterns.headlines(_,_) => Some(NewContentEvent(contentType = Some(HeadlinesType), topic = Topic.getTopic(text)))
      case Patterns.popular(_,_) => Some(NewContentEvent(contentType = Some(MostViewedType), topic = Topic.getTopic(text)))
      case Patterns.greeting(_) => Some(GreetingEvent)
      case Patterns.menu(_,_) => Some(MenuEvent(ResponseText.menu))
      case Patterns.help(_,_) => Some(MenuEvent(ResponseText.help))
      case Patterns.subscribe(_,_) => Some(SubscribeYesEvent)
      case Patterns.unsubscribe(_,_) => Some(UnsubscribeEvent)
      case _ => None
    }

    event.orElse(
      //Does it contain a topic?
      Topic.getTopic(text).map { topic =>
        NewContentEvent(contentType = Some(HeadlinesType), topic = Some(topic))
      }
    )
  }

  private def carousel(user: User, contentType: ContentType, topic: Option[Topic], offset: Int, capi: Capi, variant: Option[String] = None): Future[Result] = {
    val futureCarousel = contentType match {
      case MostViewedType => capi.getMostViewed(user.front, topic) map (contentToCarousel(_, offset, user.front, variant))
      case HeadlinesType => capi.getHeadlines(user.front, topic) map (contentToCarousel(_, offset, user.front, variant))
    }

    futureCarousel map {
      case Some(carousel) =>
        val updatedUser = user.copy(
          state = Some(Name),
          contentType = Some(contentType.name),
          contentTopic = topic.map(_.terms.headOption.getOrElse("")),
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

  private def menu(user: User, text: String): Future[Result] = {
    def getSubscriptionButton = (user: User) => {
      if (user.notificationTime != "-") {
        MessageToFacebook.Button("postback", Some("Manage subscription"), payload = Some("manage_subscription"))
      } else {
        MessageToFacebook.Button("postback", Some("Subscribe"), payload = Some("subscribe_yes"))
      }
    }

    val buttons = Seq(
      MessageToFacebook.Button("postback", Some("Headlines"), payload = Some("headlines")),
      MessageToFacebook.Button("postback", Some("Most popular"),payload = Some("most_popular")),
      getSubscriptionButton(user)
    )

    val message = MessageToFacebook.buttonsMessage(user.ID, buttons, text)

    Future.successful((State.changeState(user, MainState.Name), List(message)))
  }

  private def manageSubscription(user: User): Future[Result] = {
    if (user.notificationTime == "-") {
      SubscribeQuestionState.question(user)
    } else {
      val buttons = Seq(
        MessageToFacebook.Button("postback", Some("Change time"), payload = Some("subscribe_yes")),
        MessageToFacebook.Button("postback", Some("Change edition"), payload = Some("change_front_menu")),
        MessageToFacebook.Button("postback", Some("Unsubscribe"), payload = Some("unsubscribe"))
      )

      val message = MessageToFacebook.buttonsMessage(
        user.ID,
        buttons,
        ResponseText.manageSubscription(EditionQuestionState.frontToUserFriendly(user.front), user.notificationTime)
      )

      Future.successful((user, List(message)))
    }
  }

  private def unsubscribe(user: User): Future[Result] = {
    log(UnsubscribeLogEvent(user.ID))

    val updatedUser = user.copy(
      notificationTime = "-",
      notificationTimeUTC = "-"
    )
    val response = MessageToFacebook.textMessage(user.ID, ResponseText.unsubscribe)
    Future.successful((updatedUser, List(response)))
  }
}
