package com.gu.facebook_news_bot

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpEntity, HttpMethods, HttpRequest, MediaTypes}
import akka.stream.ActorMaterializer
import com.gu.facebook_news_bot.models.{FacebookUser, User}
import com.gu.facebook_news_bot.services.Facebook
import com.gu.facebook_news_bot.services.Facebook.GetUserSuccessResponse
import com.gu.facebook_news_bot.state.StateHandler
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

class TestService(testName: String, createUser: Boolean = false) extends BotService with MockitoSugar {
  override val facebook = mock[Facebook]
  when(facebook.getUser(anyString())).thenReturn(Future.successful(GetUserSuccessResponse(FacebookUser("en_GB", 1.25))))
  override val capi = DummyCapi
  override val stateHandler = StateHandler(facebook, capi)
  override val dynamoClient = LocalDynamoDB.client
  override val usersTable = testName  

  override implicit val system = ActorSystem("facebook-news-bot-actor-system")
  override implicit val executor = system.dispatcher
  override implicit val materializer = ActorMaterializer()

  //Create the user
  if (createUser) Await.result(userStore.updateUser(User(testName, "uk", 0, "-", "-", Some("MAIN"), Some(0))), 5.seconds)

  def getRequest(json: String) =
    HttpRequest(
      method = HttpMethods.POST,
      uri = "/webhook",
      entity = HttpEntity(MediaTypes.`application/json`, json)
    )
}
