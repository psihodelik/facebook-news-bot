package com.gu.facebook_news_bot.state

import com.gu.facebook_news_bot.models._
import com.gu.facebook_news_bot.services.{Capi, Facebook}
import com.gu.facebook_news_bot.state.StateHandler.Result
import com.gu.facebook_news_bot.stores.UserStore
import com.gu.facebook_news_bot.utils.JsonHelpers
import com.gu.facebook_news_bot.utils.Loggers.LogEvent
import io.circe.generic.auto._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

sealed trait NominationCategory { val name: String }
case object BestPicture extends NominationCategory { val name = "Best Picture" }
case object BestDirector extends NominationCategory { val name = "Best Director" }
case object BestActress extends NominationCategory { val name = "Best Actress" }
case object BestActor extends NominationCategory { val name = "Best Actor" }

object OscarsNomsStates {

  case object InitialQuestionState extends YesOrNoState {

    val Name = "OSCARS_NOMS_INITIAL_QUESTION"

    val Question = "Welcome to the Guardian Academy. Choose your favourite Oscar contenders and we'll let you know how your picks do on the night."

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

    case class Prediction(name: String, category: String)

    def transition(user: User, messaging: MessageFromFacebook.Messaging, capi: Capi, facebook: Facebook, store: UserStore): Future[Result] = {
      State.getUserInput(messaging) match {

        case Some(text) => store.OscarsStore.getUserNominations(user.ID).map { maybeUserNoms =>
          val userNoms = maybeUserNoms.getOrElse(UserNoms(user.ID))
          categoryAwaitingPrediction(userNoms) match {
            case Some(category) =>
              val nominees = buildNominationCarousel(category, user)
              val message = MessageToFacebook.textMessage(user.ID, s"Sorry I didn't get that. Who do you think will win ${category.name}?")
              (user, List(message, nominees))
            case None => finishedPlaying(user)
          }
        }
        case None => MainState.menu(user, "Ok. Is there anything else I can help you with?")
      }
    }

    def question(user: User, text: Option[String] = None): Future[Result] = {
      requestBestPicture(user)
    }

    private def requestBestPicture(user: User): Future[Result] = {
      val message = MessageToFacebook.textMessage(user.ID, "Great. Let’s start with Best Picture. Which of these deserves the Oscar?")
      val categoryNominees = buildNominationCarousel(BestPicture, user)
      Future.successful(State.changeState(user, Name), List(message,categoryNominees))
    }
    
    override def onPostback(user: User, postback: MessageFromFacebook.Postback, capi: Capi, facebook: Facebook, store: UserStore): Future[Result] = {
      val userChoice = postback.payload
      JsonHelpers.decodeJson[Prediction](userChoice) match {
        case Some(prediction) => processPrediction(prediction, user, store)
        case None =>
          val message = MessageToFacebook.textMessage(user.ID, "Sorry I didn't catch that. Could you try again?")
          Future.successful(State.changeState(user, Name), List(message))
      }
    }

    private def processPrediction(prediction: Prediction, user: User, store: UserStore): Future[Result] = {
      store.OscarsStore.getUserNominations(user.ID).map { maybeUserNoms =>
        val userNoms = maybeUserNoms.getOrElse(UserNoms(user.ID))

        getCategory(prediction.category) match {
          case Some(category) =>
            if (categoryFieldIsEmpty(category, userNoms)) {
              val newUserNoms = updateUserNoms(userNoms, category, prediction.name)
              store.OscarsStore.putUserNominations(newUserNoms)
              requestFollowUpPrediction(user, newUserNoms, prediction)
            } else {
              val expectedCategory = categoryAwaitingPrediction(userNoms)
              expectedCategory match {
                case Some(category) => {
                  val message = MessageToFacebook.textMessage(user.ID, "Sorry, I was expecting an answer for " + category.name + ".")
                  (State.changeState(user, Name), Nil)
                }
                case None => finishedPlaying(user)
              }
            }
          case None => (user, Nil)
        }
      }
    }

    private def buildNominationCarousel(category: NominationCategory, user: User): MessageToFacebook = {

      val carouselContent: List[IndividualNominee] = category match {
        case BestPicture => Nominees.bestPictureNominees
        case BestDirector => Nominees.bestDirectorNominees
        case BestActress => Nominees.bestActressNominees
        case BestActor => Nominees.bestActorNominees
      }

      val tiles = carouselContent.map { nominee =>
        val prediction = Prediction(nominee.name, category.name)

        val voteButton = MessageToFacebook.Button(
          `type` = "postback",
          title = Some("Vote"),
          payload = Some(JsonHelpers.encodeJson(prediction).noSpaces)
        )

        MessageToFacebook.Element(
          title = nominee.name,
          image_url = Some(nominee.pictureUrl),
          buttons = Some(List(voteButton))
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

    private def categoryAwaitingPrediction(userNoms : UserNoms): Option[NominationCategory] = {
      if (userNoms.bestPicture.isEmpty) Some(BestPicture)
      else if (userNoms.bestDirector.isEmpty) Some(BestDirector)
      else if (userNoms.bestActress.isEmpty) Some(BestActress)
      else if (userNoms.bestActor.isEmpty) Some(BestActor)
      else None
    }

    private def getCategory(name: String): Option[NominationCategory] = {
      name match {
        case BestPicture.name => Some(BestPicture)
        case BestDirector.name => Some(BestDirector)
        case BestActor.name => Some(BestActor)
        case BestActress.name => Some(BestActress)
        case _ => None
      }
    }

    private def categoryFieldIsEmpty(category: NominationCategory, userNoms: UserNoms): Boolean = {
      category match {
        case BestPicture => userNoms.bestPicture.isEmpty
        case BestDirector => userNoms.bestDirector.isEmpty
        case BestActress => userNoms.bestActress.isEmpty
        case BestActor => userNoms.bestActor.isEmpty
      }
    }

    private def updateUserNoms(userNoms: UserNoms, category: NominationCategory, userChoice: String): UserNoms = {
      category match {
        case BestPicture => userNoms.copy(bestPicture = Some(userChoice))
        case BestDirector => userNoms.copy(bestDirector = Some(userChoice))
        case BestActor => userNoms.copy(bestActor = Some(userChoice))
        case BestActress => userNoms.copy(bestActress = Some(userChoice))
      }
    }

    private def requestFollowUpPrediction(user: User, userNoms: UserNoms, prediction: Prediction): Result = {
      categoryAwaitingPrediction(userNoms) match {
        case Some(category) => {
          val message = MessageToFacebook.textMessage(user.ID, s"Great. I got ${prediction.name} for ${prediction.category}. Who do you think will win ${category.name}?")
          val categoryNominees = buildNominationCarousel(category, user)

          (State.changeState(user, Name), List(message,categoryNominees))
        }
        case None => finishedPlaying(user)
      }

    }

    private def finishedPlaying(user: User): Result = {
      val message = MessageToFacebook.textMessage(user.ID, "Great. That's the game completed.")
      (State.changeState(user, Name), List(message))
    }

  }

}