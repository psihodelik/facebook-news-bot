package com.gu.facebook_news_bot.services

import akka.actor.{Actor, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.{ActorMaterializer, OverflowStrategy, QueueOfferResult}
import com.gu.facebook_news_bot.models.FacebookUser
import de.heikoseeberger.akkahttpcirce.CirceSupport

import scala.concurrent.ExecutionContext.Implicits.global
import akka.contrib.throttle.TimerBasedThrottler

import scala.concurrent.duration._
import akka.contrib.throttle.Throttler.{RateInt, SetTarget}
import akka.stream.scaladsl.{Keep, Sink, Source}
import com.gu.cm.Mode
import com.gu.facebook_news_bot.BotConfig

import scala.concurrent.{Future, Promise}
import com.gu.facebook_news_bot.models.MessageToFacebook
import com.gu.facebook_news_bot.services.Facebook._
import com.gu.facebook_news_bot.utils.Loggers._
import com.gu.facebook_news_bot.utils.JsonHelpers._
import io.circe.generic.auto._
import io.circe.Decoder

import scala.util.{Failure, Success}

trait Facebook {
  def send(messages: List[MessageToFacebook]): Unit

  def getUser(id: String): Future[GetUserResult]
}
object Facebook {
  sealed trait FacebookResponse
  object FacebookResponse {
    implicit val decodeFacebookResponse: Decoder[FacebookResponse] = Decoder.instance(cursor =>
      cursor.as[FacebookSuccessResponse].orElse(cursor.as[FacebookErrorResponse])
    )
  }
  case class FacebookSuccessResponse(recipient_id: String, message_id: String) extends FacebookResponse
  case class FacebookErrorResponse(error: FacebookErrorMessage) extends FacebookResponse
  case class FacebookErrorMessage(message: String, code: Int)


  sealed trait GetUserResult
  case class GetUserSuccessResponse(user: FacebookUser) extends GetUserResult
  case object GetUserNoDataResponse extends GetUserResult
  case class GetUserError(errorResponse: FacebookErrorResponse) extends GetUserResult
}

class FacebookImpl extends Facebook with CirceSupport {

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

  def getUser(id: String): Future[GetUserResult] = {
    val port = if (BotConfig.stage == Mode.Dev) s":${BotConfig.facebook.port}" else ""
    val responseFuture = Http().singleRequest(
      HttpRequest(
        method = HttpMethods.GET,
        uri = s"${BotConfig.facebook.protocol}://${BotConfig.facebook.host}$port/${BotConfig.facebook.version}/$id?access_token=${BotConfig.facebook.accessToken}"
      )
    )

    responseFuture.flatMap { response =>
      if (response.status == StatusCodes.OK) {
        //Force the content type here because FB graph specifies "text/javascript"
        Unmarshal(response.entity.withContentType(ContentTypes.`application/json`)).to[FacebookUser].map(GetUserSuccessResponse).recover {
          /**
            * If we ask for the user's data and the user has deleted the conversation then FB returns a 200 with
            * an empty body.
            */
          case _ => GetUserNoDataResponse
        }
      } else Unmarshal(response.entity.withContentType(ContentTypes.`application/json`)).to[FacebookErrorResponse].map(GetUserError)
    }
  }

  private class FacebookActor extends Actor {

    /**
      * Use a connection pool to limit the number of open http requests to the configured number,
      * and a source queue to buffer requests if we exceed that limit.
      */
    private val pool = {
      if (BotConfig.stage != Mode.Dev) Http().cachedHostConnectionPoolHttps[Promise[HttpResponse]](host = BotConfig.facebook.host, port = BotConfig.facebook.port)
      else Http().cachedHostConnectionPool[Promise[HttpResponse]](host = BotConfig.facebook.host, port = BotConfig.facebook.port)
    }
    private val queue = Source.queue[(HttpRequest, Promise[HttpResponse])](bufferSize = 1000, OverflowStrategy.dropNew)
      .via(pool)
      .toMat(Sink.foreach {
        case ((Success(resp), p)) => p.success(resp)
        case ((Failure(e), p)) => p.failure(e)
      })(Keep.left)
      .run

    def receive = {
      case message: MessageToFacebook =>
        val responseFuture = for {
          entity <- Marshal(message).to[RequestEntity]
          strict <- entity.toStrict(5.seconds)
          response <- enqueue(strict)
        } yield response

        responseFuture.onComplete {
          case Success(response) =>
            //Always read the entire stream to avoid blocking the connection
            response.entity.toStrict(5.seconds).foreach { strictEntity =>
              appLogger.debug(s"Messenger response: $strictEntity")

              Unmarshal(strictEntity.withContentType(ContentTypes.`application/json`)).to[FacebookResponse].onComplete {
                case Success(facebookResponse: FacebookErrorResponse) =>
                  appLogger.warn(s"Error response from Facebook for user ${message.recipient.id}: $facebookResponse")
                case Success(_) =>  //No error response, do nothing
                case Failure(error) => appLogger.error(s"Error unmarshalling facebook response: ${error.getMessage}", error)
              }
            }
          case Failure(error) => appLogger.error(s"Error sending message $message to facebook: ${error.getMessage}", error)
        }
    }

    private def enqueue(strict: HttpEntity.Strict): Future[HttpResponse] = {
      appLogger.debug(s"Sending message to Facebook: $strict")

      val request = HttpRequest(
        method = HttpMethods.POST,
        uri = s"/${BotConfig.facebook.version}/me/messages?access_token=${BotConfig.facebook.accessToken}",
        entity = strict
      )
      val promise = Promise[HttpResponse]

      queue.offer(request -> promise).flatMap {
        case QueueOfferResult.Enqueued => promise.future
        case QueueOfferResult.Failure(e) => Future.failed(e)
        case _ => Future.failed(new RuntimeException())
      }
    }
  }
}
