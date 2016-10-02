package com.gu.facebook_news_bot.services

import java.util.concurrent.TimeUnit

import com.github.benmanes.caffeine.cache.Caffeine
import com.gu.contentapi.client.GuardianContentClient
import com.typesafe.scalalogging.StrictLogging
import com.gu.contentapi.client.model.ItemQuery
import com.gu.contentapi.client.model.v1.{Content, ItemResponse}
import com.gu.facebook_news_bot.BotConfig

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

trait Capi {
  def getHeadlines(front: String, topic: Option[String]): Future[Seq[Content]]

  def getMostViewed(front: String, topic: Option[String]): Future[Seq[Content]]
}

object CapiImpl extends Capi with StrictLogging {

  private lazy val client = new GuardianContentClient(BotConfig.capi.key)

  def getHeadlines(front: String, topic: Option[String]): Future[Seq[Content]] =
    doQuery(basicItemQuery(front).showEditorsPicks(), _.editorsPicks)

  def getMostViewed(front: String, topic: Option[String]): Future[Seq[Content]] =
    doQuery(basicItemQuery(front).showMostViewed(), _.mostViewed)

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
