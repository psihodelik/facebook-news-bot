package com.gu.facebook_news_bot

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.gu.facebook_news_bot.models.MessageToFacebook
import com.gu.facebook_news_bot.util.JsonHelpers
import com.gu.facebook_news_bot.util.JsonHelpers._
import de.heikoseeberger.akkahttpcirce.CirceSupport
import org.mockito.Mockito._
import org.scalatest.{FunSpec, Matchers}
import org.scalatest.mockito.MockitoSugar
import io.circe.generic.auto._

class SubscriptionTest extends FunSpec with Matchers with ScalatestRouteTest with MockitoSugar with CirceSupport {
  val TestName = "subscription_test"
  LocalDynamoDB.createTable(TestName)

  it("should ask a new user if they want to subscribe") {
    val service = new TestService(TestName)
    val request = service.getRequest(loadFile("src/test/resources/facebookRequests/newUser.json"))

    request ~> service.routes ~> check {
      status should equal(OK)

      val expectedMessage = JsonHelpers.decodeFromFile[MessageToFacebook]("src/test/resources/facebookResponses/newUser.json")
      verify(service.facebook, timeout(5000)).send(List(expectedMessage))
    }
  }

  it("should ask what time if user says yes") {
    val service = new TestService(TestName)
    val request = service.getRequest(loadFile("src/test/resources/facebookRequests/yesSubscribe.json"))

    request ~> service.routes ~> check {
      status should equal(OK)

      val expectedMessage = JsonHelpers.decodeFromFile[MessageToFacebook]("src/test/resources/facebookResponses/yesSubscribe.json")
      verify(service.facebook, timeout(5000)).send(List(expectedMessage))
    }
  }

  it("should respond to time of 6") {
    val service = new TestService(TestName)
    val request = service.getRequest(loadFile("src/test/resources/facebookRequests/briefingTime.json"))

    request ~> service.routes ~> check {
      status should equal(OK)

      val expectedMessage = JsonHelpers.decodeFromFile[MessageToFacebook]("src/test/resources/facebookResponses/briefingTime.json")
      verify(service.facebook, timeout(5000)).send(List(expectedMessage))
    }
  }

  it("should include subscription time in response to manage_subscription") {
    val service = new TestService(TestName)
    val request = service.getRequest(loadFile("src/test/resources/facebookRequests/manageSubscription.json"))

    request ~> service.routes ~> check {
      status should equal(OK)

      val expectedMessage = JsonHelpers.decodeFromFile[MessageToFacebook]("src/test/resources/facebookResponses/manageSubscription.json")
      verify(service.facebook, timeout(5000)).send(List(expectedMessage))
    }
  }

  it("should list editions in response to change_front_menu") {
    val service = new TestService(TestName)
    val request = service.getRequest(loadFile("src/test/resources/facebookRequests/changeFrontMenu.json"))

    request ~> service.routes ~> check {
      status should equal(OK)

      val expectedMessage = JsonHelpers.decodeFromFile[MessageToFacebook]("src/test/resources/facebookResponses/changeFrontMenu.json")
      verify(service.facebook, timeout(5000)).send(List(expectedMessage))
    }
  }

  it("should update front") {
    val service = new TestService(TestName)
    val request = service.getRequest(loadFile("src/test/resources/facebookRequests/changeFront.json"))

    request ~> service.routes ~> check {
      status should equal(OK)

      val expectedMessage = JsonHelpers.decodeFromFile[MessageToFacebook]("src/test/resources/facebookResponses/changeFront.json")
      verify(service.facebook, timeout(5000)).send(List(expectedMessage))
    }
  }

  it("should unsubscribe") {
    val service = new TestService(TestName)
    val request = service.getRequest(loadFile("src/test/resources/facebookRequests/unsubscribe.json"))

    request ~> service.routes ~> check {
      status should equal(OK)

      val expectedMessage = JsonHelpers.decodeFromFile[MessageToFacebook]("src/test/resources/facebookResponses/unsubscribe.json")
      verify(service.facebook, timeout(5000)).send(List(expectedMessage))
    }
  }
}
