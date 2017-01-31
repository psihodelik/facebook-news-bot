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
  LocalDynamoDB.createUsersTable(TestName)

  private def routeTest(inputFile: String, outputFile: String) = {
    val service = new TestService(TestName)
    val request = service.getRequest(loadFile(inputFile))

    request ~> service.routes ~> check {
      status should equal(OK)

      val expectedMessage = JsonHelpers.decodeFromFile[MessageToFacebook](outputFile)
      verify(service.facebook, timeout(5000)).send(List(expectedMessage))
    }
  }

  it("should ask a new user if they want to subscribe") {
    routeTest(
      "src/test/resources/facebookRequests/newUser.json",
      "src/test/resources/facebookResponses/newUser.json"
    )
  }

  it("should ask which edition if user says yes") {
    routeTest(
      "src/test/resources/facebookRequests/yesSubscribe.json",
      "src/test/resources/facebookResponses/yesSubscribe.json"
    )
  }

  it("should ask what time after setting edition") {
    routeTest(
      "src/test/resources/facebookRequests/changeFront.json",
      "src/test/resources/facebookResponses/changeFront.json"
    )
  }

  it("should respond to briefing time by asking if user wants a customised briefing") {
    routeTest(
      "src/test/resources/facebookRequests/briefingTime.json",
      "src/test/resources/facebookResponses/briefingTime.json"
    )
  }

  it("should respond to 1st topic choice by asking if user wants another topic") {
    routeTest(
      "src/test/resources/facebookRequests/customBriefingTopic.json",
      "src/test/resources/facebookResponses/customBriefingTopic.json"
    )
  }

  it("should respond to no with confirmation of subscription") {
    routeTest(
      "src/test/resources/facebookRequests/noCustomBriefingTopic.json",
      "src/test/resources/facebookResponses/noCustomBriefingTopic.json"
    )
  }

  it("should include subscription time in response to manage_morning_briefing") {
    routeTest(
      "src/test/resources/facebookRequests/manageMorningBriefing.json",
      "src/test/resources/facebookResponses/manageMorningBriefing.json"
    )
  }

  it("should display unsubscribe menu") {
    routeTest(
      "src/test/resources/facebookRequests/unsubscribeMenu.json",
      "src/test/resources/facebookResponses/unsubscribeMenu.json"
    )
  }

  it("should unsubscribe") {
    routeTest(
      "src/test/resources/facebookRequests/unsubscribe.json",
      "src/test/resources/facebookResponses/unsubscribe.json"
    )
  }
}
