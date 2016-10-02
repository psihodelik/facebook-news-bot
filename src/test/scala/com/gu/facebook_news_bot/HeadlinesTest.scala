package com.gu.facebook_news_bot

import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.gu.facebook_news_bot.models._
import com.gu.facebook_news_bot.services.Facebook
import com.gu.facebook_news_bot.state.StateHandler
import com.gu.facebook_news_bot.util.JsonHelpers
import com.gu.facebook_news_bot.util.JsonHelpers._
import de.heikoseeberger.akkahttpcirce.CirceSupport
import org.scalatest.{FunSpec, Matchers}
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.mockito.Matchers._
import io.circe.generic.auto._
import StatusCodes._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

class HeadlinesTest extends FunSpec with Matchers with ScalatestRouteTest with MockitoSugar with BotService with CirceSupport {

  val TableName = "headlines-test"
  LocalDynamoDB.createTable(TableName)

  override val facebook = mock[Facebook]
  when(facebook.getUser(anyString())).thenReturn(Future.successful(FacebookUser("mr", "test", "m", "en_GB", 0)))
  override val capi = DummyCapi
  override val stateHandler = StateHandler(facebook, capi)
  override val dynamoClient = LocalDynamoDB.client
  override val usersTable = TableName

  //Create the user
  Await.result(userStore.updateUser(User("headlines_test", "uk", 0, "-", "-", "MAIN", 1)), 5.seconds)

  it("should return uk headlines") {
    val jsonRequest = loadFile("src/test/resources/facebookRequests/headlinesRequest.json")
    val request = HttpRequest(
      method = HttpMethods.POST,
      uri = "/webhook",
      entity = HttpEntity(MediaTypes.`application/json`, jsonRequest)
    )

    request ~> routes ~> check {
      //This response is just a 200, a subsequent request is sent to 'facebook' with the message
      status should equal(OK)

      val expectedMessage = JsonHelpers.decodeFromFile[MessageToFacebook]("src/test/resources/facebookResponses/headlinesResponse.json")
      verify(facebook, timeout(5000)).send(List(expectedMessage))
    }
  }
}
