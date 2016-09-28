package com.gu.facebook_news_bot.models

/**
  * The user data as it is stored in dynamodb
  */
case class User(id: String,
                front: String,
                offsetHours: Double,
                notificationTime: String,
                notificationTimeUTC: String,
                state: String)
