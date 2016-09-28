package com.gu.facebook_news_bot.stores

import com.amazonaws.services.dynamodbv2.document._
import com.gu.facebook_news_bot.models.User

class UserStore(db: DynamoDB, usersTableName: String) {

  private val usersTable = db.getTable(usersTableName)

  def getUser(id: String): Option[User] = {
    None
  }

  def updateUser(user: User) = {
    println(user)
  }

  def createUser(user: User) = {
    println(user)
  }
}
