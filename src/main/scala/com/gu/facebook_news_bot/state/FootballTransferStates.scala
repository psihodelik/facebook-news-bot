package com.gu.facebook_news_bot.state

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import com.gu.cm.Mode
import com.gu.facebook_news_bot.BotConfig
import com.gu.facebook_news_bot.models.{MessageFromFacebook, MessageToFacebook, User}
import com.gu.facebook_news_bot.services.{Capi, Facebook}
import com.gu.facebook_news_bot.state.StateHandler.Result
import com.gu.facebook_news_bot.stores.UserStore
import com.gu.facebook_news_bot.utils.Loggers.{LogEvent, appLogger}
import de.heikoseeberger.akkahttpcirce.CirceSupport

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import io.circe.generic.auto._

object FootballTransferStates {

  private val teams: Map[String,String] = Teams.getTeams

  case object InitialQuestionState extends YesOrNoState {
    val Name = "FOOTBALL_TRANSFER_INITIAL_QUESTION"

    val Question = "Would you like to receive team updates during the January football transfer window?"
    protected def getQuestionText(user: User) = {
      if (user.version.contains(0)) s"Hi, I'm the Guardian chatbot. $Question"
      else Question
    }

    protected def yes(user: User, facebook: Facebook): Future[Result] = EnterTeamsState.question(user)

    protected def no(user: User): Future[Result] = MainState.menu(user, "Ok. Is there anything else I can help you with?")
  }

  /**
    * This state allows the user to enter a team name.
    * For the question "Is there another team you’re interested in?", it is valid for a user to answer with both:
    *   a) yes/no,
    *   b) the name of a team.
    * So this state allows both, and a user exits this state by saying "no".
    */
  case object EnterTeamsState extends State {
    val Name = "FOOTBALL_TRANSFER_ENTER_TEAMS"
    
    private val NoPattern = """\b(no|nope|nah|not)\b""".r.unanchored

    private case class NewSubscriberEvent(id: String, event: String = "football_transfers_subscribe", _eventName: String = "football_transfers_subscribe") extends LogEvent

    def transition(user: User, messaging: MessageFromFacebook.Messaging, capi: Capi, facebook: Facebook, store: UserStore): Future[Result] = {
      State.getUserInput(messaging) match {
        case Some(text) => parseText(user, store, text)
        case None => unknown(user)
      }
    }

    def question(user: User, text: Option[String] = None): Future[Result] = {
      val message = MessageToFacebook.textMessage(user.ID, text.getOrElse("OK, which team are you interested in?"))
      Future.successful(State.changeState(user, Name), List(message))
    }

    private def parseText(user: User, store: UserStore, text: String): Future[Result] = {
      text.toLowerCase match {
        case YesOrNoState.YesPattern(_) => question(user)

        case NoPattern(_) =>
          val message = {
            if (user.footballTransfers.contains(true)) "Thanks for signing up. You’ll receive updates throughout the January window.\nIs there anything else I can help you with?"
            else "Is there anything else I can help you with?"
          }
          MainState.menu(user, message)

        case other =>
          teams.get(other.trim.replaceAll("[.,!?]", "")) match {
            case Some(team) =>
              store.addTeam(user.ID, team)
              //Only log the event if it's their first subscription
              if (!user.footballTransfers.contains(true)) State.log(NewSubscriberEvent(user.ID))

              question(
                user.copy(footballTransfers = Some(true)),
                Some(s"You're now following $team. Is there another team you’re interested in?")
              )
            case None => unknown(user)
          }
      }
    }

    private def unknown(user: User): Future[Result] = question(user, Some("Sorry, I didn’t understand that. Would you like to enter another team?"))
  }

  case object ManageFootballTransfersState extends State {
    val Name = "MANAGE_FOOTBALL_TRANSFERS"

    def transition(user: User, messaging: MessageFromFacebook.Messaging, capi: Capi, facebook: Facebook, store: UserStore): Future[Result] = {
      State.getUserInput(messaging).flatMap { text =>
        val lower = text.toLowerCase
        if (lower.contains("add")) Some(EnterTeamsState.question(user))
        else if (lower.contains("remove")) Some(RemoveTeamState.question(user, store))
        else if (lower.contains("unsubscribe")) Some(unsubscribe(user, store))
        else None
      } getOrElse State.unknown(user)
    }

    def question(user: User, store: UserStore): Future[Result] = {
      val result = store.getTeams(user.ID).collect {
        case t if t.nonEmpty =>
          //Already subscribed
          val quickReplies = Seq(
            MessageToFacebook.QuickReply(title = Some("Unsubscribe"), payload = Some("unsubscribe")),
            MessageToFacebook.QuickReply(title = Some("Add teams"), payload = Some("add")),
            MessageToFacebook.QuickReply(title = Some("Remove teams"), payload = Some("remove"))
          )

          val message = MessageToFacebook.quickRepliesMessage(
            user.ID,
            quickReplies,
            "What would you like to change?"
          )

          (State.changeState(user, Name), List(message))
      }

      result.recoverWith { case _ => InitialQuestionState.question(user) }
    }

    private def unsubscribe(user: User, store: UserStore): Future[Result] = {
      store.getTeams(user.ID).map { currentTeams =>
        currentTeams.foreach(store.removeTeam(user.ID, _))

        val message = MessageToFacebook.textMessage(user.ID, "You'll no longer receive football transfer window updates.")
        val updatedUser = user.copy(state = Some(MainState.Name), footballTransfers = Some(false))
        (updatedUser, List(message))
      }
    }
  }

  case object RemoveTeamState extends State {
    val Name = "FOOTBALL_TRANSFERS_REMOVE_TEAM"

    def transition(user: User, messaging: MessageFromFacebook.Messaging, capi: Capi, facebook: Facebook, store: UserStore): Future[Result] = {
      val result = for {
        text <- State.getUserInput(messaging)
        team <- teams.get(text.toLowerCase.trim)
      } yield removeTeam(user, team, store)

      result.getOrElse(State.unknown(user))
    }

    def question(user: User, store: UserStore): Future[Result] = {
      val futureMessage = store.getTeams(user.ID).map { currentTeams =>
        if (currentTeams.nonEmpty) {
          val quickReplies = currentTeams.toList.map { t =>
            MessageToFacebook.QuickReply(title = Some(t), payload = Some(t))
          }
          MessageToFacebook.quickRepliesMessage(user.ID, quickReplies, "Which team shall I remove?")
        } else {
          appLogger.error(s"Arrived in RemoveTeamState with no teams for user ${user.ID}")
          MessageToFacebook.errorMessage(user.ID)
        }
      }


      futureMessage.map(message => (State.changeState(user, Name), List(message)))
    }

    private def removeTeam(user: User, team: String, store: UserStore): Future[Result] = {
      store.removeTeam(user.ID, team)

      val message = MessageToFacebook.textMessage(user.ID, s"You will no longer receive transfer updates for $team")
      Future.successful((State.changeState(user, MainState.Name), List(message)))
    }
  }
}

/**
  * Gets the list of teams and their aliases from the api.
  * We do this once on startup, so getTeams is blocking.
  */
private object Teams extends CirceSupport {
  implicit val system = ActorSystem("teams-actor-system")
  implicit val materializer = ActorMaterializer()

  private case class SheetsData(sheets: Sheets)
  private case class Sheets(team_list: Seq[Team])
  private case class Team(Team: String, Alternative_spellings: String)

  def getTeams: Map[String,String] = {
    if (BotConfig.stage != Mode.Dev) {
      Await.result({
        val responseFuture = Http().singleRequest(
          HttpRequest(
            method = HttpMethods.GET,
            uri = BotConfig.football.api
          )
        )

        for {
          response <- responseFuture
          entity <- response.entity.toStrict(5.seconds)
          data <- Unmarshal(entity).to[SheetsData]
        } yield {
          if (response.status != StatusCodes.OK) {
            appLogger.warn(s"Unexpected status received from ${BotConfig.football.api} for team list. Response was: $response")
            Map()
          } else {
            data.sheets.team_list.flatMap { team =>
              val aliases = team.Alternative_spellings.split(",").toList.filterNot(_ == "").map { alias =>
                alias.trim.toLowerCase -> team.Team
              }
              val names = (team.Team.toLowerCase -> team.Team) :: aliases
              getAlternateNames(team.Team) ::: names
            }.toMap
          }
        }
      }, 5.seconds)
    } else {
      //Need some test data
      Map("man utd" -> "Manchester United")
    }
  }

  private val cityPattern = """(.*) City$""".r.unanchored
  private val unitedPattern = """(.*) United$""".r.unanchored
  private def getAlternateNames(name: String): List[(String,String)] = {
    name match {
      case cityPattern(start) => List(start.toLowerCase -> name)
      case unitedPattern(start) => List(start.toLowerCase -> name, s"${start.toLowerCase} utd" -> name)
      case _ => Nil
    }
  }
}
