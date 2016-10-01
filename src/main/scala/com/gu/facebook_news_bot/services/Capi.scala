package com.gu.facebook_news_bot.services

import com.gu.contentapi.client.GuardianContentClient
import com.typesafe.scalalogging.StrictLogging

class Capi(override val apiKey: String) extends GuardianContentClient(apiKey) with StrictLogging {

}
