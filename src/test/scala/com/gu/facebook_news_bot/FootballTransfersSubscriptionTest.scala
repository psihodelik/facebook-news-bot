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

class FootballTransfersSubscriptionTest extends FunSpec with Matchers with ScalatestRouteTest with MockitoSugar with CirceSupport {
  val TestName = "football_transfers_test"
  LocalDynamoDB.createUsersTable(TestName)
  LocalDynamoDB.createUserTeamTable(s"$TestName-teams")

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
      "src/test/resources/facebookRequests/footballTransfers/newUser.json",
      "src/test/resources/facebookResponses/footballTransfers/newUser.json"
    )
  }

  it("should ask the user if they'd like team updates") {
    routeTest(
      "src/test/resources/facebookRequests/footballTransfers/yesSubscribe.json",
      "src/test/resources/facebookResponses/footballTransfers/yesSubscribe.json"
    )
  }

  it("should accept Manchester United and ask user if they want to enter another team") {
    routeTest(
      "src/test/resources/facebookRequests/footballTransfers/enterTeam.json",
      "src/test/resources/facebookResponses/footballTransfers/enterTeam.json"
    )
  }

  it("should go to menu if they do not want to enter another team") {
    routeTest(
      "src/test/resources/facebookRequests/footballTransfers/noMoreTeams.json",
      "src/test/resources/facebookResponses/footballTransfers/noMoreTeams.json"
    )
  }

  it("should display football transfer settings") {
    routeTest(
      "src/test/resources/facebookRequests/footballTransfers/manage.json",
      "src/test/resources/facebookResponses/footballTransfers/manage.json"
    )
  }

  it("should ask which team to remove") {
    routeTest(
      "src/test/resources/facebookRequests/footballTransfers/removeOption.json",
      "src/test/resources/facebookResponses/footballTransfers/removeOption.json"
    )
  }

  it("should remove a team") {
    routeTest(
      "src/test/resources/facebookRequests/footballTransfers/removeTeam.json",
      "src/test/resources/facebookResponses/footballTransfers/removeTeam.json"
    )
  }
}
