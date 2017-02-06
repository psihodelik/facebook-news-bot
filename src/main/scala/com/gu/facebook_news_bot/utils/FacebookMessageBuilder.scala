package com.gu.facebook_news_bot.utils

import com.gu.contentapi.client.model.v1.{Asset, Content, Element}
import com.gu.facebook_news_bot.BotConfig
import com.gu.facebook_news_bot.models.MessageToFacebook
import org.jsoup.Jsoup

object FacebookMessageBuilder {

  val MaxImageWidth = 1000
  val CarouselSize = 5  //Number of items in a carousel

  def contentToCarousel(contentList: Seq[Content], offset: Int, edition: String, currentTopic: Option[String], variant: Option[String] = None, includeMoreQuickReply: Boolean = true): Option[MessageToFacebook.Message] = {
    val sliced = contentList.slice(offset, offset + CarouselSize)
    if (sliced.isEmpty) None
    else {
      val tiles = sliced.map { content =>
        MessageToFacebook.Element(
          title = content.webTitle,
          item_url = Some(buildUrl(content.webUrl, variant)),
          subtitle = content.fields.flatMap(_.standfirst.map(Jsoup.parse(_).text)),
          image_url = Some(getImageUrl(content)),
          buttons = Some(List(MessageToFacebook.Button(`type` = "element_share")))
        )
      }
      val attachment = MessageToFacebook.Attachment.genericAttachment(tiles)

      val moreQuickReply = {
        if (includeMoreQuickReply) {
          MessageToFacebook.QuickReply(
            content_type = "text",
            title = Some(currentTopic.map(topic => s"More $topic").getOrElse("More stories")),
            payload = Some("more")
          )
        } else {
          MessageToFacebook.QuickReply(
            content_type = "text",
            title = Some("Headlines"),
            payload = Some("headlines")
          )
        }
      }

      Some(MessageToFacebook.Message(
        attachment = Some(attachment),
        quick_replies = Some(moreQuickReply :: topicQuickReplies(edition, currentTopic) ::: supportUsQuickReply :: Nil)
      ))
    }
  }

  def supportUsQuickReply = {
    MessageToFacebook.QuickReply(
      content_type = "text",
      title = Some("Support the Guardian"),
      payload = Some("support")
    )
  }

  def topicQuickReplies(edition: String, currentTopic: Option[String] = None): List[MessageToFacebook.QuickReply] = {
    val topicNames = suggestedTopics(edition)
    val filtered = currentTopic.map(current => topicNames.filterNot(_.toLowerCase == current)).getOrElse(topicNames)
    filtered.map(t => MessageToFacebook.QuickReply(content_type = "text", title = Some(t), payload = Some(t.toLowerCase())))
  }

  def suggestedTopics(edition: String): List[String] = edition match {
    case "us" => List("Politics", "Sport", "Business")
    case "au" => List("Politics", "Sport", "Business", "Culture")
    case "international" => List("Sport", "Business", "Technology")
    case _ => List("Politics", "Football", "Lifestyle", "Sport", "Tech")
  }

  //Include the variant as a parameter if present
  private def buildUrl(webUrl: String, variant: Option[String]): String =
    s"$webUrl?CMP=${BotConfig.campaignCode}${variant.map(v => s"&variant=$v").getOrElse("")}"

  // Look for widest image up to MaxImageWidth
  def getImageUrl(content: Content): String = {
    val widestImageAsset = for {
      elements <- content.elements
      mainElement <- elements.find(_.relation == "main").orElse(elements.headOption)
      asset <- getWidestImageAssetFromElement(mainElement)
    } yield asset

    widestImageAsset.flatMap(_.file).getOrElse(BotConfig.defaultImageUrl)
  }

  private def getWidestImageAssetFromElement(element: Element): Option[Asset] = {
    def getWidth(asset: Asset): Option[Int] = {
      for {
        data <- asset.typeData
        width <- data.width
      } yield width
    }

    element.assets.foldLeft[Option[Asset]](None) { (widestAsset, thisAsset) =>
      val widest = widestAsset.flatMap(a => getWidth(a)).getOrElse(0)
      getWidth(thisAsset) collect {
        case width if width <= MaxImageWidth && width > widest => thisAsset
      } orElse widestAsset
    }
  }

  def supportUsCarousel(userEdition: String): MessageToFacebook.Message = {
    val edition = if (userEdition == "international") "uk" else userEdition
    val campaignCode = s"INTCMP=${BotConfig.campaignCode}"

    val tiles = List(
      MessageToFacebook.Element(
        title = "Become a Guardian Supporter",
        item_url = Some(s"https://membership.theguardian.com/$edition/supporter?$campaignCode"),
        image_url = Some(BotConfig.supportersImageUrl),
        buttons = Some(List(MessageToFacebook.Button(`type` = "element_share")))
      ),
      MessageToFacebook.Element(
        title = "Contribute to the Guardian",
        item_url = Some(s"https://contribute.theguardian.com/$edition?$campaignCode"),
        image_url = Some(BotConfig.supportersImageUrl),
        buttons = Some(List(MessageToFacebook.Button(`type` = "element_share")))
      ),
      MessageToFacebook.Element(
        title = "How technology disrupted the truth",
        item_url = Some(s"https://www.theguardian.com/media/2016/jul/12/how-technology-disrupted-the-truth?$campaignCode"),
        image_url = Some("https://media.guim.co.uk/328e9120d07331a2458e2acdb2ba033fe1b672fe/0_0_5000_3000/1000.jpg"),
        buttons = Some(List(MessageToFacebook.Button(`type` = "element_share")))
      )
    )

    MessageToFacebook.Message(attachment = Some(MessageToFacebook.Attachment.genericAttachment(tiles)))
  }
}
