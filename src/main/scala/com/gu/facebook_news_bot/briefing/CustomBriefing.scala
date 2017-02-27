package com.gu.facebook_news_bot.briefing

import com.gu.contentapi.client.model.v1.Content
import com.gu.facebook_news_bot.models.{Id, MessageToFacebook, User}
import com.gu.facebook_news_bot.services.{Capi, Topic}
import com.gu.facebook_news_bot.state.StateHandler._
import com.gu.facebook_news_bot.utils.FacebookMessageBuilder
import com.gu.facebook_news_bot.utils.Loggers._
import org.joda.time.{DateTime, DateTimeZone}

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

        //Interleave main headlines with topic stories, and deduplicate
        val ops =
          appendNextNonDup(topic1Headlines) andThen
          appendNextNonDup(headlines.drop(1)) andThen
          appendNextNonDup(if (topic2Headlines.nonEmpty) topic2Headlines else topic1Headlines.drop(1)) andThen
          appendNextNonDup(headlines.drop(2))

        val stories: Seq[Content] = ops(headlines.take(1))

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
        val messages = maybeCarousel.map { carousel =>
          List(
            MorningBriefingPoller.morningMessage(user),
            MessageToFacebook(Id(user.ID), Some(carousel))
          )
        } getOrElse {
          //If we didn't get a carousel back then it's probably a CAPI issue
          appLogger.warn(s"Failed to build custom briefing carousel for user: ${user.ID}")
          Nil
        }

        (user, messages)
      }
    }
  }

  private def appendNextNonDup(next: Seq[Content]): Seq[Content] => Seq[Content] = { current =>
    current ++ next.find(content => isFresh(content) && !current.exists(_.id == content.id))
  }

  private def isFresh(content: Content): Boolean = {
    content.webPublicationDate.exists { date =>
      val pubDate = new DateTime(date.dateTime, DateTimeZone.UTC)
      DateTime.now(DateTimeZone.UTC).minusHours(24).isBefore(pubDate)
    }
  }

  def getVariant(edition: String) = s"custom-briefing-$edition"
}
