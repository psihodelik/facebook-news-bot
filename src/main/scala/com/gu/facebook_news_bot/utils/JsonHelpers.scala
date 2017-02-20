package com.gu.facebook_news_bot.utils

import com.gu.facebook_news_bot.models.MessageToFacebook
import com.gu.facebook_news_bot.utils.Loggers._
import io.circe._
import io.circe.parser._
import io.circe.syntax._
import io.circe.generic.auto._

object JsonHelpers {

  private def parseJson(s: String): Option[Json] = parse(s).fold(
    { error =>
      appLogger.error(s"Error parsing string $s. Error was $error")
      None
    },
    Some(_)
  )

  def decodeJson[T : Decoder](s: String): Option[T] = {
    parseJson(s).flatMap { json =>
      json.as[T].fold(
        { error =>
          appLogger.error(s"Error decoding string $s. Error was $error")
          None
        },
        Some(_)
      )
    }
  }

  def encodeJson[T : ObjectEncoder](item: T): Json = item.asJson

  /**
    * MessageToFacebook needs a custom encoder because circe will include a field with
    * value of None as a JNull, which Facebook doesn't like.
    * TODO - it would be more efficient to write a macro that generates an encoder that ignores
    * all None values, but I haven't been able to get it to work with marshalling...
    */
  implicit val messageToFacebookEncoder = Encoder.instance[MessageToFacebook] { message =>
    val json = encodeJson(message)
    JsonHelpers.recursivelyRemoveNulls(json)
  }

  /**
    * @param json a JObject or JArray
    * @return the Json with no JNull values
    */
  def recursivelyRemoveNulls(json: Json): Json = {
    json.arrayOrObject(
      json,
      (array: Vector[Json]) => Json.fromValues(array.map(recursivelyRemoveNulls)),
      (obj: JsonObject) =>
        Json.fromJsonObject(obj.toMap.foldLeft(obj) {
          case (acc: JsonObject, (k, v)) => if (!v.isNull) acc.add(k, recursivelyRemoveNulls(v)) else acc.remove(k)
        })
    )
  }
}
