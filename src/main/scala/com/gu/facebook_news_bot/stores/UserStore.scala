package com.gu.facebook_news_bot.stores

import cats.data.Xor
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsyncClient
import com.amazonaws.services.dynamodbv2.model.{ConditionalCheckFailedException, PutItemResult}
import com.gu.facebook_news_bot.models.User
import com.gu.scanamo.query.Not
import com.gu.scanamo.{ScanamoAsync, Table}
import com.gu.scanamo.syntax._
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * TODO -
  * Make reads strongly consistent - requires scanamo change
  */
class UserStore(client: AmazonDynamoDBAsyncClient, usersTableName: String) extends StrictLogging {

  def getUser(id: String): Future[Option[User]] = {
    val futureResult = ScanamoAsync.get[User](client)(usersTableName)('ID -> id)
    futureResult.map { result =>
      result.flatMap { parseResult =>
        //If parsing fails, log the error and we'll have to create a new user
        parseResult.fold(
          { error =>
            logger.error(s"Error parsing User data from dynamodb: $error")
            None
          }, {
            Some(_)
          }
        )
      }
    }
  }

  def updateUser(user: User): Future[Xor[ConditionalCheckFailedException, PutItemResult]] = {
    //Conditional update - if user already exists and its version has changed, do not update
    val currentVersion = user.version.getOrElse(0l)
    val newUser = user.copy(version = Some(currentVersion + 1))
    val table = Table[User](usersTableName)
    ScanamoAsync.exec(client)(table.given(Not(attributeExists('version)) or 'version -> currentVersion).put(newUser))
  }
}
