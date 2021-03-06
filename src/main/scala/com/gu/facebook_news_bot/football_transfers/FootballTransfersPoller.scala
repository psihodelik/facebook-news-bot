package com.gu.facebook_news_bot.football_transfers

import java.text.DecimalFormat

import akka.actor.Props
import com.gu.facebook_news_bot.BotConfig
import com.gu.facebook_news_bot.models.{Id, MessageToFacebook}
import com.gu.facebook_news_bot.services.Facebook.FacebookMessageResult
import com.gu.facebook_news_bot.services.{Facebook, FacebookEvents, SQSMessageBody}
import com.gu.facebook_news_bot.state.FootballTransferStates
import com.gu.facebook_news_bot.utils.Loggers._
import com.gu.facebook_news_bot.utils.{JsonHelpers, SQSPoller}
import io.circe.generic.auto._

import scala.concurrent.duration._
import scala.concurrent.Future

class FootballTransfersPoller(val facebook: Facebook) extends SQSPoller {
  val SQSName = BotConfig.football.transfersSQSName
  override val PollPeriod = 2000.millis

  protected def process(messageBody: SQSMessageBody): Future[List[FacebookMessageResult]] =
    JsonHelpers.decodeJson[UserFootballTransfer](messageBody.Message).map(processUserTransfer) getOrElse Future.successful(Nil)

  private def processUserTransfer(userTransfer: UserFootballTransfer): Future[List[FacebookMessageResult]] = {
    val message = FootballTransfersPoller.buildTransferMessage(userTransfer)
    FootballTransfersPoller.logNotification(userTransfer.userId, userTransfer.transfer.player)
    facebook.send(List(message))
  }
}

object FootballTransfersPoller {
  def props(facebook: Facebook) = Props(new FootballTransfersPoller(facebook))

  case class NotificationEventLog(id: String, event: String = "football-transfer-notification", _eventName: String = "football-transfer-notification", player: String) extends LogEvent
  def logNotification(id: String, player: String): Unit = {
    val eventLog = NotificationEventLog(id = id, player = player)
    logEvent(JsonHelpers.encodeJson(eventLog))
    FacebookEvents.logEvent(eventLog)
  }

  private val format = new DecimalFormat("0.##")
  def prettifyFee(fee: Int): String = {
    if (fee >= 1000000) s"£${format.format(fee / 1000000f)}m"
    else if (fee >= 1000) s"£${format.format(fee / 1000f)}k"
    else s"£$fee"
  }

  private def getImageUrl(transfer: FootballTransfer): String = {
    FootballTransferStates.teams.get(transfer.toClub.toLowerCase)
      .orElse(FootballTransferStates.teams.get(transfer.fromClub.toLowerCase))
      .flatMap(_.imageUrl)
      .getOrElse(BotConfig.football.defaultImageUrl)
  }

  private def buildTitle(transfer: FootballTransfer): String = {
    transfer.transferStatus match {
      case "agreed fees" => agreedFeesTitle(transfer)
      case _ => doneDealTitle(transfer)
    }
  }

  private def agreedFeesTitle(t: FootballTransfer): String = {
    //We may not actually know the fee after an "agreed fees" announcement
    t.fee match {
      case Some(fee) => s"${t.fromClub} and ${t.toClub} have agreed a fee of ${FootballTransfersPoller.prettifyFee(t.fee.get)} for ${t.player} transfer"
      case None => s"${t.fromClub} and ${t.toClub} have agreed a fee for ${t.player} transfer"
    }
  }

  private def doneDealTitle(t: FootballTransfer): String = {
    t.transferType match {
      case "released" => s"${t.fromClub} have released ${t.player}"
      case "loan ended" => s"${t.player} returns to ${t.toClub} from ${t.fromClub}"
      case "loan extended" => s"${t.player} has had his loan from ${t.fromClub} to ${t.toClub} extended"
      case "loan" => s"${t.player} has joined ${t.toClub} on loan from ${t.fromClub}"
      case "fee" if t.fee.isDefined =>
        s"${t.player} has joined ${t.toClub} from ${t.fromClub} for ${FootballTransfersPoller.prettifyFee(t.fee.get)}"
      case "undisclosed fee" => s"${t.player} has joined ${t.toClub} from ${t.fromClub} for an undisclosed fee"
      case "free agent" => s"${t.player} has joined ${t.toClub} as a free agent"
      case _ => s"${t.player} has joined ${t.toClub} from ${t.fromClub}"
    }
  }

  def buildTransferMessage(userFootballTransfer: UserFootballTransfer): MessageToFacebook = {
    val title = buildTitle(userFootballTransfer.transfer)
    val button = MessageToFacebook.Button(
      `type` = "web_url",
      url = Some(s"${BotConfig.football.interactiveUrl}?CMP=${BotConfig.campaignCode}"),
      title = Some("See more transfers")
    )
    val imageUrl = getImageUrl(userFootballTransfer.transfer)

    val attachment = MessageToFacebook.Attachment.genericAttachment(Seq(
      MessageToFacebook.Element(
        title = title,
        buttons = Some(Seq(button)),
        image_url = Some(imageUrl)
      )
    ))

    val message = MessageToFacebook.Message(
      attachment = Some(attachment),
      quick_replies = Some(List(
        MessageToFacebook.QuickReply("text", Some("Headlines"), Some("headlines")),
        MessageToFacebook.QuickReply("text", Some("Football news"), Some("football"))
      ))
    )

    MessageToFacebook(
      recipient = Id(userFootballTransfer.userId),
      message = Some(message)
    )
  }
}
