package com.gu.facebook_news_bot.utils

import scala.util.Random

object ResponseText {
  def greeting = random(List(
    "Hi there",
    "Hi",
    "Hello",
    "Hey"
  ))

  def welcome = "Hi, I'm a prototype chatbot created by the Guardian to keep you up-to-date with the latest news.\n\nWould you like me to deliver a daily morning briefing to you?"

  def unknown = random(List(
    "I'm sorry, I didn't understand that. I'm only good at simple instructions and sending out headlines at the moment",
    "I'm sorry, I didn't understand that. My creators are working hard to make me smarter and more useful for you",
    "I'm sorry, I didn't understand that. Typing 'menu' at anytime will bring up the options menu"
  ))

  def noResults = "Sorry, I don't have any stories on that at the moment"

  def menu = "How can I help?"

  def help = "I'm a prototype chatbot created by the Guardian to keep you up-to-date with the latest news.\\n\\nI can give you the headlines, the most popular stories or deliver a morning briefing to you.\\n\\nHow can I help you today?"

  def briefingTimeQuestion = "When would you like your morning briefing delivered?"

  def subscribeQuestion = "Would you like to subscribe to the daily morning briefing?"

  def subscribed(time: String) = s"Done. You will start receiving the morning briefing at $time.\n\nRemember, you can change your subscription to this at any time from the menu.\n\nWould you like to see the headlines or the most popular stories right now?"

  def subscribeNo = "No problem, maybe later then. You can subscribe to the morning briefing at any time from the menu.\n\nWould you like the headlines or the most popular stories?"

  def unsubscribe = "Done. You will no longer receive the morning briefing.\n\nYou can re-subscribe at any time from the menu"

  def manageSubscription(edition: String, time: String) = s"Your edition is currently set to $edition and your morning briefing time is $time.\n\nWhat would you like to change?"

  def editionQuestion = "Please choose a new edition:"

  def editionChanged(edition: String) = s"Your edition has been updated to $edition"

  private def random(list: List[String]) = list((Random.nextDouble * list.length).floor.toInt)
}
