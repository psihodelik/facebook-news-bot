package com.gu.facebook_news_bot.stores

import cats.data.Xor
import cats.data.OptionT
import cats.instances.future._
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBAsyncClient
import com.amazonaws.services.dynamodbv2.model.{ConditionalCheckFailedException, PutItemResult}
import com.gu.facebook_news_bot.models.{User, UserNoms, UserTeam}
import com.gu.scanamo.query.Not
import com.gu.scanamo.{ScanamoAsync, Table}
import com.gu.scanamo.syntax._
import com.gu.facebook_news_bot.utils.Loggers._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * TODO -
  * Make reads strongly consistent - requires scanamo change
  */
class UserStore(client: AmazonDynamoDBAsyncClient, usersTableName: String, userTeamTableName: String, userNomsTableName: String) {

  private val usersTable = Table[User](usersTableName)
  private val userTeamTable = Table[UserTeam](userTeamTableName)
  private val userNomsTable = Table[UserNoms](userNomsTableName)

  def getUser(id: String): Future[Option[User]] = {
    val futureResult = OptionT(ScanamoAsync.get[User](client)(usersTableName)('ID -> id))
    futureResult.map { result =>
      result.fold({ error =>
        appLogger.error(s"Error parsing User data from dynamodb: $error")
        None
      }, {
        Some(_)
      })
    }.value.map(_.flatten)
  }

  def updateUser(user: User): Future[Xor[ConditionalCheckFailedException, PutItemResult]] = {
    //Conditional update - if user already exists and its version has changed, do not update
    val currentVersion = user.version.getOrElse(0l)
    val newUser = user.copy(version = Some(currentVersion + 1))
    ScanamoAsync.exec(client)(usersTable.given(Not(attributeExists('version)) or 'version -> currentVersion).put(newUser))
  }

  object TeamStore {

    def getTeams(id: String): Future[Seq[String]] = {
      ScanamoAsync.exec(client)(userTeamTable.query('ID -> id)) map { results =>
        results.flatMap { result =>
          result.toOption.map(_.team)
        }
      }
    }

    def addTeam(id: String, team: String): Unit = ScanamoAsync.exec(client)(userTeamTable.put(UserTeam(id, team)))
    def removeTeam(id: String, team: String): Unit = ScanamoAsync.exec(client)(userTeamTable.delete('ID -> id and 'team -> team))

  }

  object OscarsStore {

    def getUserNominations(id: String): Future[Option[UserNoms]] = {
      val futureNominations = OptionT(ScanamoAsync.get[UserNoms](client)(userNomsTableName)('ID -> id))
      futureNominations.map { result =>
        result.fold({ error =>
          appLogger.error(s"Error parsing Nominations data from dynamodb: $error")
          None
        }, {
          Some(_)
        })
      }.value.map(_.flatten)
    }

    def putUserNominations(nominations: UserNoms): Unit = {
      ScanamoAsync.exec(client)(userNomsTable.put(nominations))
    }

  }

}
