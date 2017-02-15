package com.gu.facebook_news_bot.oscars_night

import akka.actor.Props
import com.gu.facebook_news_bot.BotConfig
import com.gu.facebook_news_bot.models.{Id, MessageToFacebook}
import com.gu.facebook_news_bot.services.Facebook.FacebookMessageResult
import com.gu.facebook_news_bot.services.{Capi, Facebook, SQSMessageBody}
import com.gu.facebook_news_bot.stores.UserStore
import com.gu.facebook_news_bot.utils.Loggers.appLogger
import com.gu.facebook_news_bot.utils.{JsonHelpers, SQSPoller}
import io.circe.generic.auto._

import scala.concurrent.Future

case class AwardEventSNS(userId: String, category: String, winner: String, userPrediction: String, percentageWhoAgreedWithNom: Double)

class OscarsNightPoller(val facebook: Facebook, val capi: Capi, val userStore: UserStore) extends SQSPoller {

  val SQSName = BotConfig.aws.oscarsNotificationsSQSName

  override def process(messageBody: SQSMessageBody): Future[List[FacebookMessageResult]] = {
    appLogger.info(messageBody.Message)

    JsonHelpers.decodeJson[AwardEventSNS](messageBody.Message)
      .map { awardEvent => deliverAwardNotification(awardEvent) }
      .getOrElse(Future.successful(Nil))
  }

  private def deliverAwardNotification(awardEvent: AwardEventSNS): Future[List[FacebookMessageResult]] = {
    val message = OscarsNightPoller.buildNotificationMessage(awardEvent)
    facebook.send(List(message))
  }

}

object OscarsNightPoller {
  def props(facebook: Facebook, capi: Capi, userStore: UserStore) = Props(new OscarsNightPoller(facebook, capi, userStore))

  def buildNotificationMessage(a: AwardEventSNS): MessageToFacebook = {

    val stats = a.percentageWhoAgreedWithNom*100

    val winnerNotification = a.userPrediction match {
      case a.winner => s"And the winner is...you! ${a.winner} was just named ${a.category}. The Academy and ${stats.toInt.toString}% of players agreed with you. Follow our live coverage for more."
      case _ => s"And the winner is...not you. You and ${stats.toInt.toString}% of players wanted ${a.userPrediction} to take ${a.category}, but the award goes to ${a.winner}. Follow our live coverage for more."
    }

    val message = MessageToFacebook.Message(text = Some(winnerNotification))
    MessageToFacebook(
      recipient = Id(a.userId),
      message = Some(message)
    )
  }

}