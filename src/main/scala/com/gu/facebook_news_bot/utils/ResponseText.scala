package com.gu.facebook_news_bot.utils

import scala.util.Random

object ResponseText {
  def greeting = random(List(
    "Hi there",
    "Hi",
    "Hello",
    "Hey"
  ))

  def unknown = random(List(
    "I'm sorry, I didn't understand that. I'm only good at simple instructions and sending out headlines at the moment",
    "I'm sorry, I didn't understand that. My creators are working hard to make me smarter and more useful for you",
    "I'm sorry, I didn't understand that. Typing 'menu' at anytime will bring up the options menu"
  ))

  def noResults = "Sorry, I don't have any stories on that at the moment"

  private def random(list: List[String]) = list((Random.nextDouble * list.length).floor.toInt)
}
