package com.gu.facebook_news_bot.state

import com.gu.facebook_news_bot.models.{MessageFromFacebook, User}
import com.gu.facebook_news_bot.services.{Capi, Facebook, FacebookEvents}
import com.gu.facebook_news_bot.state.StateHandler.Result
import com.gu.facebook_news_bot.stores.UserStore
import com.gu.facebook_news_bot.utils.{JsonHelpers, ResponseText}
import com.gu.facebook_news_bot.utils.Loggers._
import io.circe.ObjectEncoder

import scala.concurrent.Future

trait State {
  val Name: String
  /**
    * Define the user's state transition, and build any messages to be sent to user
    */
  def transition(user: User, message: MessageFromFacebook.Messaging, capi: Capi, facebook: Facebook, store: UserStore): Future[Result]

  /**
    * Since buttons persist after the conversation has moved on, we should try to avoid using them for state-specific behaviour.
    * Where buttons are needed by a state, override this method to define the state transition.
    */
  def onPostback(user: User, postback: MessageFromFacebook.Postback, capi: Capi, facebook: Facebook, store: UserStore): Future[Result] =
    MainState.onPostback(user, postback, capi, facebook, store)
}

object State {
  def changeState(user: User, state: String): User = user.copy(state = Some(state))

  def greeting(user: User): Future[Result] = MainState.menu(user, ResponseText.greeting)

  def unknown(user: User): Future[Result] = MainState.menu(user, ResponseText.unknown)

  def getUserInput(messaging: MessageFromFacebook.Messaging): Option[String] = {
    for {
      message <- messaging.message
      value = message.quick_reply.map(_.payload).getOrElse(message.text.getOrElse(""))
    } yield value
  }

  def log[T <: LogEvent : ObjectEncoder](event: T): Unit = {
    val json = JsonHelpers.encodeJson(event)
    logEvent(json)
    FacebookEvents.logEvent(event)
  }

  val YesPattern = """\b(yes|yeah|yep|sure|ok|okay)\b""".r.unanchored
  val NoPattern = """\b(no|nope|nah|not)\b""".r.unanchored
}
