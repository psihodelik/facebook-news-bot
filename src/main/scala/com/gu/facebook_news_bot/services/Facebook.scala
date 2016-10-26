package com.gu.facebook_news_bot.services

import akka.actor.{Actor, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import com.gu.facebook_news_bot.models.FacebookUser
import de.heikoseeberger.akkahttpcirce.CirceSupport

import scala.concurrent.ExecutionContext.Implicits.global
import akka.contrib.throttle.TimerBasedThrottler

import scala.concurrent.duration._
import akka.contrib.throttle.Throttler.{RateInt, SetTarget}
import com.gu.facebook_news_bot.BotConfig

import scala.concurrent.Future
import com.gu.facebook_news_bot.models.MessageToFacebook
import com.typesafe.scalalogging.StrictLogging
import com.gu.facebook_news_bot.utils.JsonHelpers._
import io.circe.generic.auto._

trait Facebook {
  def send(messages: List[MessageToFacebook]): Unit

  def getUser(id: String): Future[FacebookUser]
}

class FacebookImpl extends Facebook with CirceSupport with StrictLogging {

  implicit val system = ActorSystem("facebook-actor-system")
  implicit val materializer = ActorMaterializer()

  /**
    * Use the TimerBasedThrottler actor to rate-limit messages to Facebook messenger
    */
  val facebookActor = system.actorOf(Props(new FacebookActor))
  val throttler = system.actorOf(Props(
    classOf[TimerBasedThrottler],
    100 msgsPer 1.second
  ))
  throttler ! SetTarget(Some(facebookActor))

  def send(messages: List[MessageToFacebook]): Unit = {
    messages.foreach(throttler ! _)
  }

  def getUser(id: String): Future[FacebookUser] = {
    val responseFuture = Http().singleRequest(
      HttpRequest(
        method = HttpMethods.GET,
        uri = s"${BotConfig.facebook.url}/$id?access_token=${BotConfig.facebook.accessToken}"
      )
    )

    for {
      response <- responseFuture
      //Force the content type here because FB graph specifies "text/javascript"
      facebookUser <- Unmarshal(response.entity.withContentType(ContentTypes.`application/json`)).to[FacebookUser]
    } yield facebookUser
  }

  private class FacebookActor extends Actor {
    def receive = {
      case message: MessageToFacebook =>
        val responseFuture = Marshal(message).to[RequestEntity] flatMap { entity =>
          logger.debug(s"Sending message to Facebook: $entity")

          Http().singleRequest(
            request = HttpRequest(
              method = HttpMethods.POST,
              uri = s"${BotConfig.facebook.url}/me/messages?access_token=${BotConfig.facebook.accessToken}",
              entity = entity
            )
          )
        }
        responseFuture.onFailure { case error: Throwable => logger.error(s"Error sending message $message to facebook: $error") }
    }
  }
}
