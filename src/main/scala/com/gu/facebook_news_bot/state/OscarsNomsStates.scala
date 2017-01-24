package com.gu.facebook_news_bot.state

import com.gu.facebook_news_bot.models.{MessageFromFacebook, MessageToFacebook, User, UserNoms}
import com.gu.facebook_news_bot.services.{Capi, Facebook}
import com.gu.facebook_news_bot.state.StateHandler.Result
import com.gu.facebook_news_bot.stores.UserStore
import com.gu.facebook_news_bot.utils.Loggers.LogEvent
import io.circe.generic.auto._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object OscarsNomsStates {

  case object InitialQuestionState extends YesOrNoState {
    val Name = "OSCARS_NOMS_INITIAL_QUESTION"

    private case class NoEvent(id: String, event: String = "oscars_noms_subscribe_no", _eventName: String = "oscars_noms_subscribe_no") extends LogEvent

    val Question = "Would you like to play our Oscars Predictions game?"

    protected def getQuestionText(user: User) = {
      if (user.version.contains(0)) s"Hi, I'm the Guardian chatbot. $Question"
      else Question
    }

    protected def yes(user: User, facebook: Facebook): Future[Result] = EnterNomsState.question(user)

    protected def no(user: User): Future[Result] = {
      State.log(NoEvent(id = user.ID))
      MainState.menu(user, "Ok. Is there anything else I can help you with?")
    }
  }

  case object EnterNomsState extends State {

    val Name = "OSCARS_ENTER_NOMS"

    private case class NewSubscriberEvent(id: String, event: String = "oscars_noms_subscribe", _eventName: String = "oscars_noms_subscribe") extends LogEvent

    def transition(user: User, messaging: MessageFromFacebook.Messaging, capi: Capi, facebook: Facebook, store: UserStore): Future[Result] = {
      State.getUserInput(messaging) match {
        case Some(text) => enterPredictions(user, store, text)
        case None => notPlaying(user)
      }
    }

    def question(user: User, text: Option[String] = None): Future[Result] = {
      val message = MessageToFacebook.textMessage(user.ID, text.getOrElse("OK, which film do you think will win best picture?"))
      Future.successful(State.changeState(user, Name), List(message))
    }


    def enterPredictions(user: User, store: UserStore, text: String): Future[Result] = {
     val test = store.OscarsStore.getUserNoms(user.ID)
      test.flatMap { result =>
        if (result.isEmpty) {
          isPlaying(user, text, store)
        } else {
          notPlaying(user)
        }
      }

    }

    private def isPlaying(user: User, text: String, store: UserStore): Future[Result] = {
      text.toLowerCase match {
        case YesOrNoState.YesPattern(_) => question(user)
        case `text` => {
          val nom = new UserNoms(ID = user.ID, bestDirector = Some(text))
          store.OscarsStore.addNomination(nom)

          val updatedUser = {
            if (!user.oscarsNoms.contains(true)) {
              State.log(NewSubscriberEvent(user.ID))
              user.copy(
                oscarsNoms = Some(true)
              )
            } else user
          }

          question(
            updatedUser,
            Some(s"You guessed ${text} for Best Picture."))
        }
        case _ => question(user, Some("Sorry I didn't get that, could you please repeat?"))
      }

      question(user)
    }

    private def notPlaying(user: User): Future[Result] = question(user, Some("Is there anything else I can help you with?"))
  }
}