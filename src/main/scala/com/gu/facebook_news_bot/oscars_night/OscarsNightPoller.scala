package com.gu.facebook_news_bot.oscars_night

import java.util.concurrent.TimeUnit

import akka.actor.Props
import cats.data.OptionT
import cats.implicits._
import com.amazonaws.services.s3.AmazonS3Client
import com.github.benmanes.caffeine.cache.Caffeine
import com.gu.facebook_news_bot.BotConfig
import com.gu.facebook_news_bot.models.MessageToFacebook.Element
import com.gu.facebook_news_bot.models.{Id, MessageToFacebook, User}
import com.gu.facebook_news_bot.services.Facebook.FacebookMessageResult
import com.gu.facebook_news_bot.services.{Capi, Facebook, SQSMessageBody}
import com.gu.facebook_news_bot.state.OscarsNomsStates
import com.gu.facebook_news_bot.state.StateHandler.Result
import com.gu.facebook_news_bot.stores.UserStore
import com.gu.facebook_news_bot.utils.{JsonHelpers, Notifier, SQSPoller}
import io.circe.generic.auto._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global


case class AwardEventUserData(category: String, winner: String, userPrediction: String, percentageWhoAgreedWithNom: Double)
case class AwardEventSNS(userId: String, isMorningBriefing: Boolean, data: Option[AwardEventUserData])
case class AwardWinners(bestPicture: String, bestDirector: String, bestActress: String, bestActor: String)

class OscarsNightPoller(val facebook: Facebook, val capi: Capi, val userStore: UserStore) extends SQSPoller {

  val SQSName = BotConfig.aws.oscarsNotificationsSQSName

  override def process(messageBody: SQSMessageBody): Future[List[FacebookMessageResult]] = {
    JsonHelpers.decodeJson[AwardEventSNS](messageBody.Message)
      .map { awardEvent => deliverAwardNotification(awardEvent) }
      .getOrElse(Future.successful(Nil))
  }

  private def deliverAwardNotification(awardEvent: AwardEventSNS): Future[List[FacebookMessageResult]] = {

    if (awardEvent.isMorningBriefing) {
      val result = for {
        user <- OptionT(Notifier.getUser(awardEvent.userId, facebook, userStore))
        userNoms <- OptionT(userStore.OscarsStore.getUserNominations(user.ID))
      } yield {
        if (!userNoms.briefingSent.contains(true) && SQSPoller.isAfterTime(offset = user.offsetHours, afterHours = 9, afterMins = 0)) {
          userStore.OscarsStore.putUserNominations(userNoms.copy(briefingSent = Some(true)))

          OscarsNightPoller.buildMorningBriefingMessage(user) flatMap {
            case (updatedUser, messages) => Notifier.sendAndUpdate(updatedUser, messages, facebook, userStore)
          }
        } else Future.successful(Nil)
      }

      result.getOrElse(Future.successful(Nil)).flatten

    } else {
      val messages = awardEvent.data.map(result => List(OscarsNightPoller.buildRollingUpdateMessage(awardEvent.userId, result)))
        .getOrElse(Nil)
      facebook.send(messages)
    }
  }

}

object OscarsNightPoller {
  def props(facebook: Facebook, capi: Capi, userStore: UserStore) = Props(new OscarsNightPoller(facebook, capi, userStore))

  def buildRollingUpdateMessage(userId: String, a: AwardEventUserData): MessageToFacebook = {

    val stats = a.percentageWhoAgreedWithNom * 100

    val winnerNotification = a.userPrediction match {
      case a.winner => s"And the winner is...you! ${a.winner} was just named ${a.category}. The Academy and ${stats.toInt.toString}% of players agreed with you. Follow our live coverage for more."
      case _ => s"And the winner is...not you. You and ${stats.toInt.toString}% of players wanted ${a.userPrediction} to take ${a.category}, but the award goes to ${a.winner}. Follow our live coverage for more."
    }

    val message = MessageToFacebook.Message(
      attachment = Some(MessageToFacebook.Attachment.buttonsAttachment(
        Seq(MessageToFacebook.Button(`type` = "web_url", title = Some("Live coverage"), url = Some("https://theguardian.com/film/live/2017/feb/26/oscars-2017-live-red-carpet-ceremony-aftermath"))),
        text = winnerNotification
      ))
    )
    MessageToFacebook(
      recipient = Id(userId),
      message = Some(message)
    )
  }

  private def buildWinnersList(a: AwardWinners): MessageToFacebook.Message = {
    val oscarWinners = List(
      Element(title = "Best Picture", subtitle = Some(s"${a.bestPicture} received the Oscar for Best Picture")),
      Element(title = "Best Director", subtitle = Some(s"${a.bestDirector} received the Oscar for Best Director")),
      Element(title = "Best Actress", subtitle = Some(s"${a.bestActress} received the Oscar for Best Actress")),
      Element(title = "Best Actor", subtitle = Some(s"${a.bestActor} received the Oscar for Best Actor"))
    )

    val listAttachment = MessageToFacebook.Attachment.plainListAttachment(oscarWinners)

    MessageToFacebook.Message(attachment = Some(listAttachment))

  }

  private def getWinners: Option[MessageToFacebook.Message] = {

    OscarsWinnersCache.get("Oscar Winners").map(m => Some(m._1))
      .getOrElse {
        //Not already cached - read the winners file from S3 and build a message
        val s3Client: AmazonS3Client = new AmazonS3Client()
        val stream = s3Client.getObject("facebook-news-bot-oscar-award-winner", BotConfig.oscarsNight.s3Path).getObjectContent
        val s3Data = scala.io.Source.fromInputStream(stream).mkString
        val winnerData: Option[AwardWinners] = JsonHelpers.decodeJson[AwardWinners](s3Data)

        val maybeWinners = winnerData.map { winners =>
          val message = buildWinnersList(winners)
          OscarsWinnersCache.put("Oscar Winners", (message, winners))
          message
        }
        maybeWinners
      }
  }

  def buildMorningBriefingMessage(user: User): Future[Result] = {

    val message = MessageToFacebook.textMessage(user.ID, "Hello! Here's your update on the Oscar winners.")

    val awardMessage = getWinners.map { maybeMessage =>
      MessageToFacebook(
        recipient = Id(user.ID),
        message = Some(maybeMessage)
      )
    }

    OscarsNomsStates.MorningNotificationState.question(user) map { case (updatedUser, questionMessage) =>
      awardMessage match {
        case Some(awards) => (updatedUser, List(message, awards) ++ questionMessage)
        case _ => (user, Nil)
      }
    }
  }
}

object OscarsWinnersCache {
  private val cache = Caffeine.newBuilder()
    .build[String, (MessageToFacebook.Message, AwardWinners)]()

  def get(key: String): Option[(MessageToFacebook.Message, AwardWinners)] = Option(cache.getIfPresent(key))
  def put(key: String, cachedTuple: (MessageToFacebook.Message, AwardWinners)): Unit = cache.put(key, cachedTuple)
}

