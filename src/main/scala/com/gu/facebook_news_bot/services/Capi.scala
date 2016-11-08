package com.gu.facebook_news_bot.services

import java.util.concurrent.TimeUnit

import com.github.benmanes.caffeine.cache.Caffeine
import com.gu.contentapi.client.GuardianContentClient
import com.gu.contentapi.client.model.ItemQuery
import com.gu.contentapi.client.model.v1.{Content, ItemResponse}
import com.gu.facebook_news_bot.BotConfig

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.matching.Regex

trait Capi {
  def getHeadlines(edition: String, topic: Option[Topic]): Future[Seq[Content]]

  def getMostViewed(edition: String, topic: Option[Topic]): Future[Seq[Content]]
}

object CapiImpl extends Capi {

  private lazy val client = new GuardianContentClient(BotConfig.capi.key)

  def getHeadlines(edition: String, topic: Option[Topic]): Future[Seq[Content]] =
    doQuery(basicItemQuery(topic.map(_.getPath(edition)).getOrElse(edition)).showEditorsPicks(), _.editorsPicks)

  def getMostViewed(edition: String, topic: Option[Topic]): Future[Seq[Content]] =
    doQuery(basicItemQuery(topic.map(_.getPath(edition)).getOrElse(edition)).showMostViewed(), _.mostViewed)

  private def doQuery(query: ItemQuery, getResults: (ItemResponse => Option[Seq[Content]])): Future[Seq[Content]] = {
    CapiCache.get(query.toString).map(cached => Future.successful(cached)).getOrElse {
      client.getResponse(query) map { response: ItemResponse =>

        val results = getResults(response).filter(_.nonEmpty)
          .orElse(response.results)   //Fall back on main results
          .getOrElse(Nil)

        CapiCache.put(query.toString, results)
        results
      }
    }
  }

  private def basicItemQuery(item: String) = ItemQuery(item)
    .showFields("standfirst")
    .tag("type/article")
    .tag("-tone/minutebyminute")
    .showElements("image")
    .pageSize(25)   //Matches the number of editors-picks/most-viewed, in case they aren't available
}

object CapiCache {

  private val cache = Caffeine.newBuilder()
    .expireAfterWrite(2, TimeUnit.MINUTES)
    .build[String, Seq[Content]]()

  def get(query: String): Option[Seq[Content]] = Option(cache.getIfPresent(query))
  def put(query: String, results: Seq[Content]): Unit = cache.put(query, results)
}

object Topic {
  def getTopic(text: String): Option[Topic] = {
    TopicList.find(topic => topic.pattern.findFirstIn(text).isDefined)
  }

  private val TopicList: List[Topic] = List(
    PoliticsTopic,

    EditionSectionTopic(List("sport","sports"), "sport"),
    EditionSectionTopic(List("film","films","movies"), "film"),
    EditionSectionTopic(List("tv","television","radio"), "tv-and-radio"),
    EditionSectionTopic("business"),
    EditionSectionTopic(List("lifestyle", "life and style"), "lifeandstyle"),
    EditionSectionTopic("environment"),
    EditionSectionTopic("money"),
    EditionSectionTopic(List("tech", "technology"), "technology"),
    EditionSectionTopic("travel"),
    EditionSectionTopic("culture"),

    SectionTopic(List("football","soccer"), "football"),
    SectionTopic("music"),
    SectionTopic("books"),
    SectionTopic(List("art","design"), "artanddesign"),
    SectionTopic("stage"),
    SectionTopic("fashion"),
    SectionTopic("science"),

    SectionTagTopic(List("rugby","rugby union"), "sport", "rugby-union"),
    SectionTagTopic(List("formula 1","formula one","f1"), "sport", "formulaone"),
    SectionTagTopic(List("horse racing"), "sport", "horse-racing"),
    SectionTagTopic(List("rugby league"), "sport", "rugbyleague"),
    SectionTagTopic("sport", "cricket"),
    SectionTagTopic("sport", "tennis"),
    SectionTagTopic("sport", "golf"),
    SectionTagTopic("sport", "cycling"),
    SectionTagTopic("sport", "boxing"),
    SectionTagTopic("technology", "games"),
    SectionTagTopic(List("food","drink"), "lifeandstyle", "food-and-drink"),
    SectionTagTopic(List("health","wellbeing"), "lifeandstyle", "health-and-wellbeing"),
    SectionTagTopic("lifeandstyle", "family"),
    SectionTagTopic("lifeandstyle", "women"),
    SectionTagTopic(List("cats","cat facts"), "lifeandstyle", "cats"),
    SectionTagTopic(List("climate"), "environment", "climate-change")
  )
}

sealed trait Topic {
  val terms: List[String]
  lazy val pattern: Regex = ("""(^|\W)(""" + terms.mkString("|") + """)($|\W)""").r.unanchored
  def getPath(edition: String): String
  def name: String = terms.headOption.getOrElse("")
}

case class SectionTopic(terms: List[String], section: String) extends Topic {
  def getPath(edition: String) = section
}
object SectionTopic {
  def apply(section: String): SectionTopic = SectionTopic(List(section), section)
}

case class EditionSectionTopic(terms: List[String], section: String) extends Topic {
  def getPath(edition: String) = {
    if (List("us","au").contains(edition.toLowerCase)) s"$edition/$section"
    else s"uk/$section"   //uk edition by default
  }
}
object EditionSectionTopic {
  def apply(section: String): EditionSectionTopic = EditionSectionTopic(List(section), section)
}

case class SectionTagTopic(terms: List[String], section: String, tag: String) extends Topic {
  def getPath(edition: String) = s"$section/$tag"
}
object SectionTagTopic {
  def apply(section: String, tag: String): SectionTagTopic = SectionTagTopic(List(tag), section, tag)
}

case object PoliticsTopic extends Topic {
  val terms = List("politics")

  def getPath(edition: String): String = {
    edition.toLowerCase match {
      case "us" => "us-news/us-politics"
      case "au" => "australia-news/australian-politics"
      case _ => "politics"
    }
  }
}
