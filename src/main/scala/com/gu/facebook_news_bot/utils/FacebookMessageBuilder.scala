package com.gu.facebook_news_bot.utils

import com.gu.contentapi.client.model.v1.{Asset, Content, Element}
import com.gu.facebook_news_bot.BotConfig
import com.gu.facebook_news_bot.models.MessageToFacebook
import org.jsoup.Jsoup

object FacebookMessageBuilder {

  val MaxImageWidth = 1000
  val CarouselSize = 5  //Number of items in a carousel

  def contentToCarousel(contentList: Seq[Content], offset: Int): Option[MessageToFacebook.Message] = {
    val sliced = contentList.slice(offset, offset + CarouselSize)
    if (sliced.isEmpty) None
    else {
      val tiles = sliced.map { content =>
        MessageToFacebook.Element(
          title = content.webTitle,
          item_url = Some(s"${content.webUrl}?CMP=${BotConfig.campaignCode}"),
          subtitle = content.fields.flatMap(_.standfirst.map(Jsoup.parse(_).text)),
          image_url = Some(getImageUrl(content)),
          buttons = Some(List(MessageToFacebook.Button(`type` = "element_share")))
        )
      }
      val attachment = MessageToFacebook.Attachment.genericAttachment(tiles)

      Some(MessageToFacebook.Message(
        attachment = Some(attachment),
        //TODO - topic quick_replies
        quick_replies = Some(List(MessageToFacebook.QuickReply(
          content_type = "text",
          title = Some("More stories"),
          payload = Some("more")
        )))
      ))
    }
  }

  // Look for widest image up to MaxImageWidth
  private def getImageUrl(content: Content): String = {
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
}
