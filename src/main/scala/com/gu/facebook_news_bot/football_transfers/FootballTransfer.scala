package com.gu.facebook_news_bot.football_transfers

case class FootballTransfer(
   player: String,
   fromClub: String,
   toClub: String,
   fromLeague: String,
   toLeague: String,
   transferStatus: String,
   transferType: String,
   fee: Option[Int])

case class UserFootballTransfer(userId: String, transfer: FootballTransfer)
