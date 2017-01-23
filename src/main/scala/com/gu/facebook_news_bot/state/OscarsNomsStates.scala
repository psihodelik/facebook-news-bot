package com.gu.facebook_news_bot.state

import com.gu.facebook_news_bot.models.{MessageFromFacebook, MessageToFacebook, User}
import com.gu.facebook_news_bot.services.{Capi, Facebook}
import com.gu.facebook_news_bot.state.StateHandler.Result
import com.gu.facebook_news_bot.stores.UserStore
import com.gu.facebook_news_bot.utils.Loggers.LogEvent
import io.circe.generic.auto._

import scala.concurrent.Future

object OscarsNomsStates {

  case object InitialQuestionState extends YesOrNoState {
    val Name = "OSCARS_NOMS_INITIAL_QUESTION"

    private case class NoEvent(id: String, event: String = "oscars_noms_subscribe_no", _eventName: String = "oscars_noms_subscribe_no", isSubscriber: Boolean) extends LogEvent

    val Question = "Would you like to play our Oscars Nomination game?"

    protected def getQuestionText(user: User) = {
      if (user.version.contains(0)) s"Hi, I'm the Guardian chatbot. $Question"
      else Question
    }

    protected def yes(user: User, facebook: Facebook): Future[Result] = EnterNomsState.question(user)

    protected def no(user: User): Future[Result] = {
      State.log(NoEvent(id = user.ID, isSubscriber = user.notificationTimeUTC != "-"))
      MainState.menu(user, "Ok. Is there anything else I can help you with?")
    }
  }

  case object EnterNomsState extends State {

    val Name = "OSCARS_ENTER_NOMS"

    def transition(user: User, messaging: MessageFromFacebook.Messaging, capi: Capi, facebook: Facebook, store: UserStore): Future[Result] = {
      State.getUserInput(messaging) match {
        case Some(text) => subscribe(user, store)
        case None => notPlaying(user)
      }
    }

    def question(user: User, text: Option[String] = None): Future[Result] = {
      val message = MessageToFacebook.textMessage(user.ID, text.getOrElse("Who do you think would win best director?"))
      Future.successful(State.changeState(user, Name), List(message))
    }

    def subscribe(user: User, store: UserStore): Future[Result] = {
      if (!user.oscarsNoms.contains(true)) {
        user.copy(oscarsNoms = Some(true))
      } else {
        user
      }
      isPlaying(user)
    }

    private def isPlaying(user: User): Future[Result] = question(user, Some("Great!"))
    private def notPlaying(user: User): Future[Result] = question(user, Some("I'm sorry that you don't want to play"))
  }
}