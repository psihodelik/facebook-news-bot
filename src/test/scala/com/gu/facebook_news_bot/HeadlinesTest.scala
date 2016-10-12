package com.gu.facebook_news_bot

import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.gu.facebook_news_bot.models._
import com.gu.facebook_news_bot.util.JsonHelpers
import com.gu.facebook_news_bot.util.JsonHelpers._
import de.heikoseeberger.akkahttpcirce.CirceSupport
import org.scalatest.{FunSpec, Matchers}
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import io.circe.generic.auto._
import StatusCodes._

class HeadlinesTest extends FunSpec with Matchers with ScalatestRouteTest with MockitoSugar with CirceSupport {

  val TableName = "headlines_test"
  LocalDynamoDB.createTable(TableName)

  it("should return uk headlines") {
    val service = new TestService(TableName, true)
    val request = service.getRequest(loadFile("src/test/resources/facebookRequests/headlines.json"))

    request ~> service.routes ~> check {
      //This response is just a 200, a subsequent request is sent to 'facebook' with the message
      status should equal(OK)

      val expectedMessage = JsonHelpers.decodeFromFile[MessageToFacebook]("src/test/resources/facebookResponses/headlines.json")
      verify(service.facebook, timeout(5000)).send(List(expectedMessage))
    }
  }

  it("should return more uk headlines") {
    val service = new TestService(TableName)
    val request = service.getRequest(loadFile("src/test/resources/facebookRequests/moreHeadlinesQuickReply.json"))

    request ~> service.routes ~> check {
      status should equal(OK)

      val expectedMessage = JsonHelpers.decodeFromFile[MessageToFacebook]("src/test/resources/facebookResponses/moreHeadlines.json")
      verify(service.facebook, timeout(5000)).send(List(expectedMessage))
    }
  }

  it("should return uk headlines after postback (button click)") {
    val service = new TestService(TableName)
    val request = service.getRequest(loadFile("src/test/resources/facebookRequests/menuHeadlines.json"))

    request ~> service.routes ~> check {
      status should equal(OK)

      val expectedMessage = JsonHelpers.decodeFromFile[MessageToFacebook]("src/test/resources/facebookResponses/headlines.json")
      verify(service.facebook, timeout(5000)).send(List(expectedMessage))
    }
  }
}
