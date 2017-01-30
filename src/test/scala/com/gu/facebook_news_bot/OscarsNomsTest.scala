package com.gu.facebook_news_bot

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import com.gu.facebook_news_bot.models.MessageToFacebook
import com.gu.facebook_news_bot.util.JsonHelpers
import com.gu.facebook_news_bot.util.JsonHelpers._
import de.heikoseeberger.akkahttpcirce.CirceSupport
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FunSpec, Matchers}


class OscarsNomsTest extends FunSpec with Matchers with ScalatestRouteTest with MockitoSugar with CirceSupport {
  val TestName = "oscars_noms_test"
  LocalDynamoDB.createUsersTable(TestName)
  LocalDynamoDB.createUserNomsTable(s"$TestName-oscarNoms")

  private def routeTest(inputFile: String, outputFile: String) = {
    val service = new TestService(TestName)
    val request = service.getRequest(loadFile(inputFile))

    request ~> service.routes ~> check {
      status should equal(OK)

      val expectedMessage = JsonHelpers.decodeFromFile[Seq[MessageToFacebook]](outputFile)
      verify(service.facebook, timeout(5000)).send(expectedMessage.toList)
    }
  }

  it("should ask a new user if they want to submit their Oscar winners predictions") {
    routeTest(
      "src/test/resources/facebookRequests/oscarsNoms/newUser.json",
      "src/test/resources/facebookResponses/oscarsNoms/newUser.json"
    )
  }

  it("should ask a user who they think will win the Best Picture category") {
    routeTest(
      "src/test/resources/facebookRequests/oscarsNoms/enterBestPicture.json",
      "src/test/resources/facebookResponses/oscarsNoms/enterBestPicture.json"
    )
  }

  it("should confirm a user's submission for Best Picture and ask who they think will win Best Director") {
    routeTest(
      "src/test/resources/facebookRequests/oscarsNoms/enterBestDirector.json",
      "src/test/resources/facebookResponses/oscarsNoms/enterBestDirector.json"
    )
  }

}
