package com.gu.facebook_news_bot

import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.stream.{ActorMaterializer, Materializer}
import akka.http.scaladsl.unmarshalling.PredefinedFromEntityUnmarshallers
import com.amazonaws.regions.Regions
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsyncClient
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException
import com.gu.cm.Mode
import com.gu.facebook_news_bot.briefing.MorningBriefingPoller
import com.gu.facebook_news_bot.models.{MessageFromFacebook, MessageToFacebook, User}
import com.gu.facebook_news_bot.services.{Capi, CapiImpl, Facebook, FacebookImpl}
import de.heikoseeberger.akkahttpcirce.CirceSupport
import io.circe.generic.auto._
import com.gu.facebook_news_bot.state.StateHandler
import com.gu.facebook_news_bot.stores.UserStore
import com.gu.facebook_news_bot.utils.{JsonHelpers, Verification}
import com.gu.facebook_news_bot.utils.{KinesisAppenderConfig, LogStash}
import com.gu.facebook_news_bot.utils.Loggers._

import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success}

trait BotService extends CirceSupport with PredefinedFromEntityUnmarshallers {
  implicit val system: ActorSystem
  implicit def executor: ExecutionContextExecutor
  implicit val materializer: Materializer

  val dynamoClient: AmazonDynamoDBAsyncClient
  val capi: Capi
  val facebook: Facebook
  val stateHandler: StateHandler
  val usersTable: String

  lazy val userStore = new UserStore(dynamoClient, usersTable)

  val routes = path("status") {
    get {
      complete("OK")
    }
  } ~ path("webhook") {
    post {
      //Verify the signature header before processing the request
      optionalHeaderValueByName("X-Hub-Signature") { signature =>
        entity(as[Array[Byte]]) { bytes =>
          if (BotConfig.stage == Mode.Dev || signature.exists(Verification.verifySignature(_, bytes))) {

            entity(as[MessageFromFacebook]) { fromFb =>
              complete {
                /**
                  * A message from FB may contains many 'entries', each with many 'messagings', from any number of users.
                  * The order in which they are processed is not important.
                  * We respond immediately with a 200.
                  */
                val messagings: Seq[MessageFromFacebook.Messaging] = for {
                  entry <- fromFb.entry
                  messaging <- entry.messaging
                } yield messaging

                messagings foreach { messaging =>
                  //For now, ignore message receipts
                  if (messaging.message.isDefined || messaging.postback.isDefined) processMessaging(messaging)
                }
              }
            }
          } else {
            appLogger.warn(s"Invalid X-Hub-Signature header, rejecting POST request.")
            complete(StatusCodes.Forbidden)
          }
        }
      }
    }
  }

  case class MessageLog(event: String, messaging: MessageFromFacebook.Messaging, user: Option[User])

  def processMessaging(msg: MessageFromFacebook.Messaging, retry: Int = 0): Unit = {

    val futureResult = for {
      user <- userStore.getUser(msg.sender.id)
      result <- stateHandler.process(user, msg)
    } yield {
      logEvent(JsonHelpers.encodeJson(MessageLog("receipt", msg, user)))
      result
    }

    futureResult onComplete {
      case Success((updatedUser, messages)) =>
        /**
          * Update user state in dynamo, then send messages
          */
        userStore.updateUser(updatedUser) foreach { userUpdateResult =>
          userUpdateResult.fold({ error: ConditionalCheckFailedException =>
            //User has since been updated in dynamo, try again with the latest version
            if (retry < 3) processMessaging(msg, retry+1)
            else {
              //Something has gone very wrong
              appLogger.error(s"Failed to update user state multiple times. User is $updatedUser and error is ${error.getMessage}", error)
            }
          }, { _ =>
            logEvent(JsonHelpers.encodeJson(MessageLog("complete", msg, Some(updatedUser))))
            facebook.send(messages)
          })
        }
      case Failure(error) =>
        appLogger.error(s"Error processing message $msg: ${error.getMessage}", error)
        facebook.send(List(MessageToFacebook.errorMessage(msg.sender.id)))
    }
  }
}

object Bot extends App with BotService {
  override implicit val system = ActorSystem("facebook-news-bot-actor-system")
  override implicit val executor = system.dispatcher
  override implicit val materializer = ActorMaterializer()

  override val capi = CapiImpl
  override val facebook = new FacebookImpl()
  override val stateHandler = StateHandler(facebook, capi)
  override val usersTable = BotConfig.aws.usersTable

  override val dynamoClient: AmazonDynamoDBAsyncClient = {
    if (BotConfig.stage == Mode.Dev) {
      new AmazonDynamoDBAsyncClient().withEndpoint("http://localhost:8000")
    } else {
      val awsRegion = Regions.fromName(BotConfig.aws.region)
      new AmazonDynamoDBAsyncClient(BotConfig.aws.CredentialsProvider).withRegion(awsRegion)
    }
  }

  BotConfig.aws.loggingKinesisStreamName match {
    case Some(stream) =>
      val appenderConfig = KinesisAppenderConfig(stream, BotConfig.aws.CredentialsProvider)
      LogStash.init(appenderConfig)
    case None => appLogger.warn(s"No kinesis stream name found for logging.")
  }

  val poller = system.actorOf(MorningBriefingPoller.props(userStore, capi, facebook))

  val bindingFuture = Http().bindAndHandle(routes, "0.0.0.0", BotConfig.port)
}
