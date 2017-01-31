package com.gu.facebook_news_bot.state

import com.gu.facebook_news_bot.models._
import com.gu.facebook_news_bot.services.{Capi, Facebook}
import com.gu.facebook_news_bot.state.StateHandler.Result
import com.gu.facebook_news_bot.stores.UserStore
import com.gu.facebook_news_bot.utils.Loggers.LogEvent
import io.circe.generic.auto._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

sealed trait NominationCategory
case object BestPicture extends NominationCategory
case object BestDirector extends NominationCategory
case object BestActress extends NominationCategory
case object BestActor extends NominationCategory

object OscarsNomsStates {

  case object InitialQuestionState extends YesOrNoState {

    val Name = "OSCARS_NOMS_INITIAL_QUESTION"

    val Question = "Welcome to the Guardian Academy. Choose your favourite Oscar contenders and we’ll let you know how your picks do on the night, how your taste compares to other readers and we’ll keep you updated with the latest news ahead of the awards. Ready to vote?"

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

    def question(user: User, text: Option[String] = None): Future[Result] = {
      requestBestPicture(user)
    }

    // This function is called when the user writes something in this state, which they should not.
    def transition(user: User, messaging: MessageFromFacebook.Messaging, capi: Capi, facebook: Facebook, store: UserStore): Future[Result] = {
      State.getUserInput(messaging) match {
        case Some(text) => store.OscarsStore.getUserNominations(user.ID).flatMap(
          maybeUserNominations => {
            val userNoms = maybeUserNominations.getOrElse(UserNoms(user.ID))
            requestFollowUpPrediction(user, userNoms)
          })
        // The above expression means that as it stands the user is forced to finish the game
        case None => notPlaying(user)
      }
    }

    private def missingCategoryFromUserNominations(userNoms : UserNoms): NominationCategory = {
      if( userNoms.bestPicture.isEmpty ) BestPicture
      else if( userNoms.bestDirector.isEmpty ) BestDirector
      else if( userNoms.bestActress.isEmpty ) BestActress
      else BestActor
    }

    private def previousQuestionCategoryFromUserNominations(userNoms : UserNoms): NominationCategory = {
      if(userNoms.bestDirector.isEmpty ) BestPicture
      else if(userNoms.bestActress.isEmpty) BestDirector
      else BestActress
    }

    private def previousUserChoiceFromUserNominations(userNoms : UserNoms): String = {
      previousQuestionCategoryFromUserNominations(userNoms) match {
        case BestPicture => userNoms.bestPicture.getOrElse("")
        case BestDirector => userNoms.bestDirector.getOrElse("")
        case BestActress => userNoms.bestActress.getOrElse("")
        case BestActor => userNoms.bestActor.getOrElse("")
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
        MessageToFacebook.Element(
          title = nominee.name,
          image_url = Some(nominee.pictureUrl),
          buttons = Some(List(MessageToFacebook.Button(`type` = "postback", title = Some("Vote"), payload = Some(nominee.name))))
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

    private def updateUserNominations(userNoms: UserNoms, userChoice: String): UserNoms = {
      val awardCategory = previousQuestionCategoryFromUserNominations(userNoms)
      awardCategory match {
        case BestPicture => userNoms.copy(bestPicture = Some(userChoice))
        case BestDirector => userNoms.copy(bestDirector = Some(userChoice))
        case BestActress => userNoms.copy(bestActress = Some(userChoice))
        case BestActor => userNoms.copy(bestActor = Some(userChoice))
      }
    }

    private def requestBestPicture(user: User): Future[Result] = {
      val message = MessageToFacebook.textMessage(user.ID, "Great. Let’s start with Best Picture. Which of these deserves the Oscar?")
      val categoryNominees = buildNominationCarousel(BestPicture, user)
      Future.successful(State.changeState(user, Name), List(message,categoryNominees))
    }
    
    override def onPostback(user: User, postback: MessageFromFacebook.Postback, capi: Capi, facebook: Facebook, store: UserStore): Future[Result] = {
      val userChoice = postback.payload
      val futureMaybeExistingUserNominations = store.OscarsStore.getUserNominations(user.ID)
      futureMaybeExistingUserNominations.flatMap{ maybeExistinguserNominations =>
        val existingUserNominations =  maybeExistinguserNominations.getOrElse(UserNoms(user.ID))
        val newUserNominations = updateUserNominations(existingUserNominations, userChoice)
        store.OscarsStore.putUserNominations(newUserNominations)
        requestFollowUpPrediction(user, newUserNominations)
      }
    }

    private def requestFollowUpPrediction(user: User, userNoms: UserNoms): Future[Result] = {
      val category = missingCategoryFromUserNominations(userNoms)
      val userAnswerFromPreviousQuestion = previousUserChoiceFromUserNominations(userNoms)
      val previousQuestionCategory = previousQuestionCategoryFromUserNominations(userNoms)
      previousQuestionCategory match {
        case BestActor => {
          val message = MessageToFacebook.textMessage(user.ID, s"Great. I got ${userAnswerFromPreviousQuestion} for ${previousQuestionCategory}.")
          Future.successful(State.changeState(user, Name), List(message))
          UpdateTypeState.question(user)

        }
        case _ => {
          val message = MessageToFacebook.textMessage(user.ID, s"Great. I got ${userAnswerFromPreviousQuestion} for ${previousQuestionCategory}. Who do you think will win ${category.toString}?")
          val categoryNominees = buildNominationCarousel(category, user)
          Future.successful(State.changeState(user, Name), List(message,categoryNominees))
        }
      }
    }

    private def notPlaying(user: User): Future[Result] = question(user, Some("Is there anything else I can help you with?"))

  }


  case object UpdateTypeState extends State {

    val Name = "OSCARS_UPDATE_TYPE_QUESTION"

    def transition(user: User, messaging: MessageFromFacebook.Messaging, capi: Capi, facebook: Facebook, store: UserStore): Future[Result] = {
      State.getUserInput(messaging).flatMap { text =>
        val lower = text.toLowerCase
        if (lower.contains("rolling-updates")) Some(updateUserUpdateType(user, true))
        else if (lower.contains("morning-briefing")) Some(updateUserUpdateType(user, false))
        else None
      } getOrElse State.unknown(user)
    }

    def question(user: User, text: Option[String] = None): Future[Result] = {

      val quickReplies = Seq(
        MessageToFacebook.QuickReply(title = Some("Rolling updates"), payload = Some("rolling-updates")),
        MessageToFacebook.QuickReply(title = Some("Morning briefing"), payload = Some("morning-briefing"))
      )

      val message = MessageToFacebook.quickRepliesMessage(
        user.ID,
        quickReplies,
        "When would you like your Oscar winner updates?"
      )

      Future.successful(State.changeState(user, Name), List(message))
    }

    def updateUserUpdateType(user: User, rollingUpdates: Boolean): Future[Result] = {
      val updatedUser = user.copy(
        state = Some(MainState.Name),
        oscarsNoms = Some(true),
        oscarsNomsUpdateType = Some(rollingUpdates)
      )

      val message = MessageToFacebook.textMessage(user.ID, "Great. That's all we need. Is there anything else I can help you with?")
      Future.successful(updatedUser, List(message))
    }

  }
}