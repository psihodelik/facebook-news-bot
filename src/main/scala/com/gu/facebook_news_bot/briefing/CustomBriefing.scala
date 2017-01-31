package com.gu.facebook_news_bot.briefing

import com.gu.contentapi.client.model.v1.Content
import com.gu.facebook_news_bot.models.{Id, MessageToFacebook, User}
import com.gu.facebook_news_bot.services.{Capi, Topic}
import com.gu.facebook_news_bot.state.StateHandler._
import com.gu.facebook_news_bot.utils.FacebookMessageBuilder
import com.gu.facebook_news_bot.utils.Loggers._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object CustomBriefing {

  /**
    * Returns a morning briefing only if the user has signed up for custom briefings
    */
  def getBriefing(user: User, capi: Capi): Option[Future[Result]] = {
    user.briefingTopic1.map { topic1 =>
      val futureMaybeCarousel = for {
        headlines <- capi.getHeadlines(user.front, None)
        topic1Headlines <- capi.getHeadlines(user.front, Topic.getTopic(topic1))
        //2nd topic is optional
        topic2Headlines <- user.briefingTopic2.map(topic2 => capi.getHeadlines(user.front, Topic.getTopic(topic2))).getOrElse(Future.successful(Nil))
      } yield {

        //Interleave main headlines with topic stories
        val stories: List[Content] = List(
          headlines.headOption,
          topic1Headlines.headOption,
          headlines.lift(1),
          topic2Headlines.headOption orElse topic1Headlines.lift(1),
          headlines.lift(2)
        ).flatten

        FacebookMessageBuilder.contentToCarousel(
          contentList = stories,
          offset = 0,
          edition = user.front,
          currentTopic = None,
          variant = Some(getVariant(user.front)),
          includeMoreQuickReply = false
        )
      }

      futureMaybeCarousel.map { maybeCarousel =>
        val messages = maybeCarousel.map(carousel => List(MessageToFacebook(Id(user.ID), Some(carousel))))
          .getOrElse {
            //If we didn't get a carousel back then it's probably a CAPI issue
            appLogger.warn(s"Failed to build custom briefing carousel for user: ${user.ID}")
            Nil
          }

        (user, messages)
      }
    }
  }

  def getVariant(edition: String) = s"custom-briefing-$edition"
}
