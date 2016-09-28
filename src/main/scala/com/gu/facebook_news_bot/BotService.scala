package com.gu.facebook_news_bot

import akka.http.scaladsl.Http
import akka.http.scaladsl.server.Directives._
import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter}
import akka.stream.{ActorMaterializer, Materializer}
import com.amazonaws.regions.Regions
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import com.amazonaws.services.dynamodbv2.document.DynamoDB
import com.gu.cm.Mode
import com.gu.facebook_news_bot.models.MessageFromFacebook
import com.gu.facebook_news_bot.services.{Capi, Facebook}
import de.heikoseeberger.akkahttpcirce.CirceSupport
import io.circe.generic.auto._
import com.gu.facebook_news_bot.state.StateHandler
import com.gu.facebook_news_bot.stores.UserStore

import scala.concurrent.ExecutionContextExecutor

trait BotService extends CirceSupport {
  implicit val system: ActorSystem
  implicit def executor: ExecutionContextExecutor
  implicit val materializer: Materializer
  val logger: LoggingAdapter

  val userStore = {
    val usersTable = BotConfig.aws.usersTable
    val client: AmazonDynamoDBClient = {
      if (BotConfig.stage == Mode.Dev) {
        new AmazonDynamoDBClient().withEndpoint("http://localhost:8000")
      } else {
        val awsRegion = Regions.fromName(BotConfig.aws.region)
        new AmazonDynamoDBClient(BotConfig.aws.CredentialsProvider).withRegion(awsRegion)
      }
    }
    new UserStore(new DynamoDB(client), usersTable)
  }

  val facebook = new Facebook(BotConfig.facebook.url, BotConfig.facebook.accessToken)
  val capi = new Capi(BotConfig.capi.key)
  val stateHandler = StateHandler(facebook, capi)

  val routes = path("status") {
    get {
      complete("OK")
    }
  } ~ path("webhook") {
    post {
      entity(as[MessageFromFacebook]) { fromFb =>
        complete {
          /**
            * A message from FB may contains many 'entries', each with many 'messagings', from any number of users.
            * The order in which they are processed is not important.
            */
          val messagings: List[MessageFromFacebook.Messaging] = for {
            entry <- fromFb.entry
            messaging <- entry.messaging
          } yield messaging

          messagings foreach { msg =>
            val user = userStore.getUser(msg.sender.id)
            val futureResult = stateHandler.process(user, msg)

            /**
              * Update user state in dynamo, then send messages
              */
            futureResult foreach { case (updatedUser, messages) =>
              if (user.isEmpty) userStore.createUser(updatedUser) else userStore.updateUser(updatedUser)
              facebook.send(messages)
            }
          }
        }
      }
    }
  }
}

object Bot extends App with BotService {
  override implicit val system = ActorSystem("facebook-news-bot-actor-system")
  override implicit val executor = system.dispatcher
  override implicit val materializer = ActorMaterializer()
  override val logger = Logging(system, getClass)

  val bindingFuture = Http().bindAndHandle(routes, "0.0.0.0", BotConfig.port)
}
