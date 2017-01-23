package com.gu.facebook_news_bot.models

case class UserNoms(ID: String,
                    bestPicture: Option[String] = None,
                    bestDirector: Option[String] = None,
                    bestActor: Option[String] = None,
                    bestActress: Option[String] = None,
                    version: Option[Long] = None)
