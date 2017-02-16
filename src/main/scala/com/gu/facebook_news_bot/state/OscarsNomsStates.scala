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

    val Question = "Welcome to the Guardian Academy. Choose your favourite Oscar contenders and we'll let you know how your picks do on the night. Ready to vote?"

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
      val message = MessageToFacebook.textMessage(user.ID, "Let’s start with Best Picture. Which of these deserves the Oscar?")
      val categoryNominees = buildNominationCarousel(BestPicture, user)
      Future.successful(State.changeState(user, Name), List(message, categoryNominees))
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
        text = None,
        attachment = Some(attachment),
        quick_replies = None,
        metadata = None)

      MessageToFacebook(Id(user.ID), Some(message))

    }

    private def categoryAwaitingPrediction(userNoms: UserNoms): Option[NominationCategory] = {
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

          val message = MessageToFacebook.textMessage(user.ID, s"I got ${prediction.name} for ${prediction.category}. ${generateText(category)}")
          val categoryNominees = buildNominationCarousel(category, user)

          (State.changeState(user, Name), List(message, categoryNominees))
        }
        case None => finishedPlaying(user)
      }

    }

    def generateText(category: NominationCategory): String = {
      category match {
        case BestDirector => "Fingers crossed! Who of the following should pick up Best Director?"
        case BestActress => "Interesting choice...Now which leading actress gave the best performance?"
        case BestActor => "Mmm...Ok! If you're sure...Now who should the Best Actor Oscar go to?"
        case _ => ""
      }
      // there should never be an error, or nothing returned for the question copy.
    }

    private def finishedPlaying(user: User): Result = {
      UpdateTypeState.question(user)
    }
  }

  case object UpdateTypeState extends State {

    val Name = "OSCARS_UPDATE_TYPE_QUESTION"

    private case class YesEvent(id: String, event: String = "oscars_noms_subscribe_yes", _eventName: String = "oscars_noms_subscribe_yes", isSubscriber: Boolean) extends LogEvent

    def transition(user: User, messaging: MessageFromFacebook.Messaging, capi: Capi, facebook: Facebook, store: UserStore): Future[Result] = {
      State.getUserInput(messaging).flatMap { text =>
        val lower = text.toLowerCase
        if (lower.contains("rolling-updates")) Some(updateUserUpdateType(user, "rolling-updates", store))
        else if (lower.contains("morning-briefing")) Some(updateUserUpdateType(user, "morning-briefing", store))
        else None
      } getOrElse State.unknown(user)
    }

    def question(user: User, text: Option[String] = None): Result = {

      val quickReplies = Seq(
        MessageToFacebook.QuickReply(title = Some("Rolling updates"), payload = Some("rolling-updates")),
        MessageToFacebook.QuickReply(title = Some("Morning briefing"), payload = Some("morning-briefing"))
      )

      val message = MessageToFacebook.quickRepliesMessage(
        user.ID,
        quickReplies,
        "Thanks for your votes. We’ll be in touch with stats, news and results. Would you like your Oscar winner updates as they come in on the night, or with your morning briefing?"
      )

      (State.changeState(user, Name), List(message))
    }

    def updateUserUpdateType(user: User, rollingUpdates: String, store: UserStore): Future[Result] = {

      store.OscarsStore.getUserNominations(user.ID).flatMap {
        case Some(noms) => {
          val updatedNoms = noms.copy(oscarsNomsUpdateType = Some(rollingUpdates))

          store.OscarsStore.putUserNominations(updatedNoms)

          val updatedUser = user.copy(
            state = Some(MainState.Name),
            oscarsNoms = Some(true)
          )

          State.log(YesEvent(user.ID, isSubscriber = user.notificationTimeUTC != "-"))

          val shareButton = getSocialShareElement(updatedUser)
          val attachment = MessageToFacebook.Attachment.genericAttachment(Seq(shareButton))
          val closingRemarks = MessageToFacebook.textMessage(user.ID, "Know another film fan? Share the next message with a friend by clicking the button below and see how they get on.")
          val quickReplies = Seq(
            MessageToFacebook.QuickReply(title = Some("Get Morning Briefing"),payload = Some("subscription")),
            MessageToFacebook.QuickReply(title = Some("Headlines"), payload = Some("headlines"))
          )

          val socialShare = MessageToFacebook(
            Id(user.ID),
            Some(MessageToFacebook.Message(
              attachment = Some(attachment),
              quick_replies = Some(quickReplies)
            ))
          )

          Future.successful(updatedUser, List(closingRemarks, socialShare))

        }
        case None => State.unknown(user)
      }
    }

    def getSocialShareElement(user: User): MessageToFacebook.Element = {
      MessageToFacebook.Element(
        title = "Challenge your friends",
        item_url = Some("http://m.me/979106075541023?ref=oscars_noms_share"),
        image_url = Some(OscarTile.imageUrl),
        subtitle = Some("Please click the button below to share with your friends!"),
        buttons = Some(List(
          MessageToFacebook.Button(`type` = "element_share")
        ))
      )
    }
  }
}