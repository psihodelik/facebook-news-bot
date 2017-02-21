package com.gu.facebook_news_bot

import com.gu.facebook_news_bot.models.{Id, MessageToFacebook}
import com.gu.facebook_news_bot.oscars_night.{AwardEventSNS, OscarsNightPoller}
import org.scalatest.{FlatSpec, Matchers}

class OscarWinnerTest extends FlatSpec with Matchers {

  it should "build a Oscar winner message" in {
    val awardWinner = AwardEventSNS(
      userId = "4",
      category = "Best Picture",
      winner = "Arrival",
      userPrediction = "Fences",
      percentageWhoAgreedWithNom = 0.3012
    )

    val expectedMessage = MessageToFacebook(
      recipient = Id("4"),
      message = Some(
        MessageToFacebook.Message(
          text = Some("And the winner is...not you. You and 30% of players wanted Fences to take Best Picture, but the award goes to Arrival. Follow our live coverage for more.")
        )
      )
    )

    OscarsNightPoller.buildNotificationMessage(awardWinner) should be(expectedMessage)

  }
}
