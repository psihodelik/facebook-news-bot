package com.gu.facebook_news_bot.util

import com.gu.facebook_news_bot.models.MessageToFacebook
import io.circe.{Decoder, DecodingFailure, Json}

import scala.io.Source._
import io.circe.parser._
import io.circe.generic.auto._

object JsonHelpers {

  implicit def messagesDecoder = Decoder.instance[Seq[MessageToFacebook]] { c =>
    c.value.asArray.map { array: Vector[Json] =>
      Right(array.flatMap(_.as[MessageToFacebook].right.toOption))
    } getOrElse Left(DecodingFailure("This JSON is not an array!", c.history))
  }

  def loadFile(path: String): String = fromFile(path).mkString

  def loadJson(path: String): Json = parse(loadFile(path)).fold(e => sys.error(s"Error parsing $path: ${e.getMessage}"), identity)

  def decodeFromFile[T : Decoder](path: String): T = loadJson(path).as[T].fold(e => sys.error(s"Error decoding $path: ${e.getMessage}"), identity)
}
