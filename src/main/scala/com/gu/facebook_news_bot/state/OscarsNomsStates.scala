package com.gu.facebook_news_bot.state

import com.gu.contentapi.client.model.v1.Content
import com.gu.facebook_news_bot.models._
import com.gu.facebook_news_bot.services.{Capi, Facebook, Topic}
import com.gu.facebook_news_bot.state.StateHandler.Result
import com.gu.facebook_news_bot.stores.UserStore
import com.gu.facebook_news_bot.utils.FacebookMessageBuilder.contentToCarousel
import com.gu.facebook_news_bot.utils.Loggers.LogEvent
import com.gu.facebook_news_bot.utils.ResponseText
import io.circe.generic.auto._
import org.jsoup.Jsoup

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait NominationCategory
object BestPicture extends NominationCategory
object BestDirector extends NominationCategory
object BestActress extends NominationCategory
object BestActor extends NominationCategory

object OscarNomsStatesHelper {

  def missingCategoryFromUserNominations(userNoms : UserNoms): NominationCategory = {
    if(userNoms.bestPicture.isEmpty){
      BestPicture
    }else if(userNoms.bestDirector.isEmpty){
      BestDirector
    }else if(userNoms.bestActress.isEmpty){
      BestActress
    }else{
      BestActor
    }
  }

  def previousQuestionCategoryFromUserNominations(userNoms : UserNoms): NominationCategory = {
    if(userNoms.bestDirector.isEmpty){
      BestPicture
    }else if(userNoms.bestActress.isEmpty){
      BestDirector
    }else{
      BestActress
    }
  }

  def previousUserChoiceFromUserNominations(userNoms : UserNoms): String = {
    "Arrival"
  }

  def buildNominationCarousel(category: NominationCategory, user: User): MessageToFacebook = {

    val carouselContent: List[IndividualNominee] = category match {
      case BestPicture => Nominees.bestPictureNominees
      case BestDirector => Nominees.bestDirectorNominees
      case BestActress => Nominees.bestActressNominees
      case BestActor => Nominees.bestActorNominees
    }

    val tiles = carouselContent.map { nominee =>
      MessageToFacebook.Element(
        title = nominee.name,
        image_url = Some(nominee.pictureUrl),
        buttons = Some(List(MessageToFacebook.Button(`type` = "")))
      )
    }

    val attachment = MessageToFacebook.Attachment.genericAttachment(tiles)

    val message = MessageToFacebook.Message(
      text =  None,
      attachment = Some(attachment),
      quick_replies = None,
      metadata = None)

    MessageToFacebook( Id(user.ID), Some(message) )

  }

}

object OscarsNomsStates {

  case object InitialQuestionState extends YesOrNoState {

    val Name = "OSCARS_NOMS_INITIAL_QUESTION"

    val Question = "Would you like to play our Oscars Predictions game?"

    protected def getQuestionText(user: User) = {
      if (user.version.contains(0)) s"Hi, I'm the Guardian chatbot. $Question"
      else Question
    }

    protected def yes(user: User, facebook: Facebook): Future[Result] = EnterNomsState.question(user)

    private case class NoEvent(id: String, event: String = "oscars_noms_subscribe_no", _eventName: String = "oscars_noms_subscribe_no") extends LogEvent

    protected def no(user: User): Future[Result] = {
      State.log(NoEvent(id = user.ID))
      MainState.menu(user, "Ok. Is there anything else I can help you with?")
    }

  }

  case object EnterNomsState extends State {

    val Name = "OSCARS_ENTER_NOMS"

    private val NoPattern = """\b(no|nope|nah|not)\b""".r.unanchored

    private case class NewSubscriberEvent(id: String, event: String = "oscars_noms_subscribe", _eventName: String = "oscars_noms_subscribe") extends LogEvent

    def question(user: User, text: Option[String] = None): Future[Result] = {
      requestPrediction(user, UserNoms(user.ID))
    }

    // This function is called when the user writes something in this state
    // Which they should not.
    def transition(user: User, messaging: MessageFromFacebook.Messaging, capi: Capi, facebook: Facebook, store: UserStore): Future[Result] = {
      State.getUserInput(messaging) match {
        case Some(text) => enterPredictions(user, store, text)
        case None => notPlaying(user)
      }
    }

    // This function handles the answer from the carousel choice (buttons)

    //    override def onPostback(user: User, postback: MessageFromFacebook.Postback, capi: Capi, facebook: Facebook, store: UserStore): Future[Result] = {
    //get UserNom
    //      val submittedPredictions = store.OscarsStore.getUserNominations(user.ID)
    //check postback data is valid
    //update UserNom with new prediction
    // check if prediction is in list, and send next element as category
    // list.isEmpty => updateState
    //requestPrediction(cat)
    //    }


    def enterPredictions(user: User, store: UserStore, text: String): Future[Result] = {
      val predictions = store.OscarsStore.getUserNominations(user.ID)
      predictions.flatMap { result =>
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
        case NoPattern(_) => question(user, Some("Sorry I didn't get that, could you please repeat?"))
        case _ => {
          store.OscarsStore. putUserNominations( UserNoms(user.ID, Some(text)) )
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

      }
    }

    private def notPlaying(user: User): Future[Result] = question(user, Some("Is there anything else I can help you with?"))

    def requestPrediction(user: User, userNoms: UserNoms): Future[Result] = {
      OscarNomsStatesHelper.missingCategoryFromUserNominations(userNoms) match {
        case BestPicture => {
          val message = MessageToFacebook.textMessage(user.ID, "Which of the following do you think will win Best Picture?")
          val categoryNominees = OscarNomsStatesHelper.buildNominationCarousel(BestPicture, user)
          Future.successful(State.changeState(user, Name), List(message,categoryNominees))
        }
        case other => {
          val userAnswerFromPreviousQuestion = OscarNomsStatesHelper.previousUserChoiceFromUserNominations(userNoms)
          val previousQuestionCategory = OscarNomsStatesHelper.previousQuestionCategoryFromUserNominations(userNoms)
          val message = MessageToFacebook.textMessage(user.ID, s"Great. I got ${userAnswerFromPreviousQuestion} for ${previousQuestionCategory}. Who do you think will win ${other.toString}?")
          val categoryNominees = OscarNomsStatesHelper.buildNominationCarousel(other, user)
          Future.successful(State.changeState(user, Name), List(message,categoryNominees))
        }
      }
    }

  }

}