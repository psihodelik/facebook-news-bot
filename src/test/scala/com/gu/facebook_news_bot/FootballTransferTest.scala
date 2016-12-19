package com.gu.facebook_news_bot

import com.gu.facebook_news_bot.football_transfers.{FootballTransfer, FootballTransfersPoller, UserFootballTransfer}
import com.gu.facebook_news_bot.models.{Id, MessageToFacebook}
import org.scalatest.{FlatSpec, Matchers}

class FootballTransferTest extends FlatSpec with Matchers {
  it should "prettify 2 million" in {
    FootballTransfersPoller.prettifyFee(2000000) should be("£2m")
  }

  it should "prettify 2.2 million" in {
    FootballTransfersPoller.prettifyFee(2200000) should be("£2.2m")
  }

  it should "prettify 222.5 thousand" in {
    FootballTransfersPoller.prettifyFee(222500) should be("£222.5k")
  }

  it should "prettify 200 quid?!" in {
    FootballTransfersPoller.prettifyFee(200) should be("£200")
  }

  it should "build a transfer message" in {
    val transfer = UserFootballTransfer(
      userId = "3",
      transfer = FootballTransfer(
        player = "Joe Davies",
        fromClub = "Leicester City",
        toClub = "Manchester United",
        fromLeague = "Premier League",
        toLeague = "Premier League",
        transferStatus = "done deal",
        transferType = "fee",
        fee = Some(2210000)
      )
    )

    val expectedMessage = MessageToFacebook(
      recipient = Id("3"),
      message = Some(MessageToFacebook.Message(
        attachment = Some(MessageToFacebook.Attachment(
          `type` = "template",
          payload = MessageToFacebook.Payload(
            template_type = "generic",
            elements = Some(Seq(
              MessageToFacebook.Element(
                title = "Joe Davies has joined Manchester United from Leicester City for £2.21m",
                image_url = Some("fake-url/file.png"),
                buttons = Some(List(MessageToFacebook.Button(
                  `type` = "web_url",
                  title = Some("See more transfers"),
                  url = Some("?CMP=fb_newsbot")
                )))
              )
            ))
          )
        ))
      ))
    )

    FootballTransfersPoller.buildTransferMessage(transfer) should be(expectedMessage)
  }
}
