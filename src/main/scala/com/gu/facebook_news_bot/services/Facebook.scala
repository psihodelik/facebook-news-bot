package com.gu.facebook_news_bot.services

import akka.actor.{Actor, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.pattern.ask
import akka.pattern.pipe
import akka.stream.{ActorMaterializer, OverflowStrategy, QueueOfferResult}
import com.gu.facebook_news_bot.models.FacebookUser
import de.heikoseeberger.akkahttpcirce.CirceSupport

import scala.concurrent.ExecutionContext.Implicits.global
import akka.contrib.throttle.TimerBasedThrottler

import scala.concurrent.duration._
import akka.contrib.throttle.Throttler.{RateInt, SetTarget}
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.util.Timeout
import com.gu.cm.Mode
import com.gu.facebook_news_bot.BotConfig

import scala.concurrent.{Future, Promise}
import com.gu.facebook_news_bot.models.MessageToFacebook
import com.gu.facebook_news_bot.services.Facebook._
import com.gu.facebook_news_bot.utils.Loggers._
import com.gu.facebook_news_bot.utils.JsonHelpers._
import io.circe.generic.auto._
import io.circe._
import io.circe.ObjectEncoder

import scala.util.{Failure, Success}

trait Facebook {
  def send(messages: List[MessageToFacebook]): Future[List[FacebookMessageResult]]

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


  sealed trait FacebookMessageResult
  case object FacebookMessageSuccess extends FacebookMessageResult
  case object FacebookMessageFailure extends FacebookMessageResult
}

object FacebookEvents extends CirceSupport {
  implicit val system = ActorSystem("facebook-events-actor-system")
  implicit val materializer = ActorMaterializer()

  // https://developers.facebook.com/docs/app-events/bots-for-messenger
  private case class EventRequest[T <: LogEvent : ObjectEncoder](event: String, custom_events: List[T], extinfo: List[String], page_id: String, page_scoped_user_id: String, advertiser_tracking_enabled: Int, application_tracking_enabled: Int)
  private object EventRequest {
    def apply[T <: LogEvent : ObjectEncoder](eventData: T): EventRequest[T] = {
      EventRequest(
        event = "CUSTOM_APP_EVENTS",
        custom_events = List(eventData),
        extinfo = List("mb1"),
        page_id = BotConfig.facebook.pageId,
        page_scoped_user_id = eventData.id,
        advertiser_tracking_enabled = 0,
        application_tracking_enabled = 0
      )
    }
  }

  def logEvent[T <: LogEvent : ObjectEncoder](eventData: T): Unit = {
    if (BotConfig.stage != Mode.Dev) {
      val eventRequest = EventRequest(eventData)

      Marshal(eventRequest).to[RequestEntity] map { requestEntity =>
        val responseFuture = Http().singleRequest(
          HttpRequest(
            method = HttpMethods.POST,
            uri = s"https://graph.facebook.com/${BotConfig.facebook.appId}/activities",
            entity = requestEntity
          )
        )

        responseFuture onComplete {
          case Success(response) =>
            response.entity.toStrict(5.seconds) //Always read the entire stream
            if (response.status != StatusCodes.OK) appLogger.warn(s"Unexpected status received after sending event log to FB analytics. Response was: $response")
          case Failure(error) => appLogger.warn(s"Error sending event log to FB analytics: ${error.getMessage}", error)
        }
      }
    }
  }
}

class FacebookImpl extends Facebook with CirceSupport {

  implicit val system = ActorSystem("facebook-actor-system")
  implicit val materializer = ActorMaterializer()

  implicit val timeout = Timeout(5.seconds)

  /**
    * Use the TimerBasedThrottler actor to rate-limit messages to Facebook messenger
    */
  val facebookActor = system.actorOf(Props(new FacebookActor))
  val throttler = system.actorOf(Props(
    classOf[TimerBasedThrottler],
    100 msgsPer 1.second
  ))
  throttler ! SetTarget(Some(facebookActor))

  def send(messages: List[MessageToFacebook]): Future[List[FacebookMessageResult]] = {
    val result = messages.map { message =>
      (facebookActor ? message).mapTo[FacebookMessageResult]
    }
    Future.sequence(result)
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
      case message: MessageToFacebook => processMessage(message) pipeTo sender
    }

    private def processMessage(message: MessageToFacebook): Future[FacebookMessageResult] = {
      val responseFuture = for {
        entity <- Marshal(message).to[RequestEntity]
        strict <- entity.toStrict(5.seconds)
        response <- enqueue(strict)
      } yield response

      responseFuture.flatMap { response =>
        //Always read the entire stream to avoid blocking the connection
        response.entity.toStrict(5.seconds).flatMap { strictEntity =>
          appLogger.debug(s"Messenger response: $strictEntity")

          Unmarshal(strictEntity.withContentType(ContentTypes.`application/json`)).to[FacebookResponse].map {
            case facebookResponse: FacebookErrorResponse =>
              appLogger.warn(s"Error response from Facebook for user ${message.recipient.id}: $facebookResponse")
              FacebookMessageFailure
            case other => FacebookMessageSuccess
          }
        }
      } recover { case error =>
        appLogger.error(s"Error sending message $message to facebook: ${error.getMessage}", error)
        FacebookMessageFailure
      }
    }

    private case class EnqueueException(result: QueueOfferResult) extends Throwable
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
        case QueueOfferResult.Dropped => Future.failed(EnqueueException(QueueOfferResult.Dropped))
        case QueueOfferResult.QueueClosed => Future.failed(EnqueueException(QueueOfferResult.QueueClosed))
      }
    }
  }
}
