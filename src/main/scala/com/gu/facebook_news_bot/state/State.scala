package com.gu.facebook_news_bot.state

import com.gu.facebook_news_bot.models.{MessageFromFacebook, MessageToFacebook, User}
import com.gu.facebook_news_bot.services.{Capi, Facebook}
import com.gu.facebook_news_bot.state.StateHandler.Result
import com.gu.facebook_news_bot.utils.{JsonHelpers, ResponseText}
import com.gu.facebook_news_bot.utils.Loggers._
import io.circe.ObjectEncoder

import scala.concurrent.Future

trait State {
  val Name: String
  /**
    * Define the user's state transition, and build any messages to be sent to user
    */
  def transition(user: User, message: MessageFromFacebook.Messaging, capi: Capi, facebook: Facebook): Future[Result]

  /**
    * Each State can optionally perform additional logging using an object with type LogEvent.
    * It will be logged as JSON
    */
  protected trait LogEvent {
    val id: String    //user's ID
    val event: String  //the name of the event being logged
  }
  protected def log[T <: LogEvent : ObjectEncoder](event: T): Unit = {
    logEvent(JsonHelpers.encodeJson(event))
  }
}

object State {
  def changeState(user: User, state: String): User = user.copy(state = Some(state))

  def greeting(user: User): Future[Result] = MainState.menu(user, ResponseText.greeting)

  def unknown(user: User): Future[Result] = {
    Future.successful((changeState(user, MainState.Name), List(MessageToFacebook.textMessage(user.ID, ResponseText.unknown))))
  }

  def getUserInput(messaging: MessageFromFacebook.Messaging): Option[String] = {
    for {
      message <- messaging.message
      value = message.quick_reply.map(_.payload).getOrElse(message.text.getOrElse(""))
    } yield value
  }
}
