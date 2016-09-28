package com.gu.facebook_news_bot.services

import com.gu.facebook_news_bot.models.MessageToFacebook

class Facebook(url: String, accessToken: String) {

  def send(messages: List[MessageToFacebook]): Unit = {
    println(messages)
  }

  def getOffset(id: String): Double = {
    0
  }
}
