package com.gu.facebook_news_bot

import com.gu.facebook_news_bot.models.User
import com.gu.facebook_news_bot.stores.UserStore
import org.scalatest.{FunSpec, Matchers}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}

class UserStoreTest extends FunSpec with Matchers with ScalaFutures {

  implicit val defaultPatience =
    PatienceConfig(timeout = Span(2, Seconds), interval = Span(15, Millis))

  val TableName = "user-store-test"
  LocalDynamoDB.createTable(TableName)
  val userStore = new UserStore(LocalDynamoDB.client, TableName)

  it("should return None for new user") {
    userStore.getUser("1").futureValue should be(None)
  }

  it("should create new user") {
    val futureResult = userStore.updateUser(User("1", "uk", 0, "-", "-", "NEW_USER", 0))
    whenReady(futureResult) { _ =>
      userStore.getUser("1").futureValue should not be None
    }
  }

  it("should update user if version hasn't changed") {
    val futureResult = userStore.updateUser(User("1", "us", 0, "-", "-", "MAIN", 1))
    whenReady(futureResult) { _ =>
      val user = userStore.getUser("1").futureValue
      user should not be None
      user.foreach { u =>
        u.front should be("us")
        u.version should be(2)
      }
    }
  }

  it("should not update user if version has changed") {
    val futureResult = userStore.updateUser(User("1", "au", 0, "-", "-", "MAIN", 1))  //version is now 2 in dynamo
    whenReady(futureResult) { result =>
      result.toOption should be(None)

      val user = userStore.getUser("1").futureValue
      user should not be None
      user.foreach { u =>
        u.front should be("us")
        u.version should be(2)
      }
    }
  }
}
