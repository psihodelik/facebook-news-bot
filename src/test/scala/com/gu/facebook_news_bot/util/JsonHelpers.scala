package com.gu.facebook_news_bot.util

import cats.data.Xor
import com.gu.facebook_news_bot.models.MessageToFacebook
import io.circe.{Decoder, DecodingFailure, Json}

import scala.io.Source._
import io.circe.parser._
import io.circe.generic.auto._

object JsonHelpers {

  implicit def messagesDecoder = Decoder.instance[Seq[MessageToFacebook]] { c =>
    c.top.asArray.map { array: List[Json] =>
      Xor.Right(array.flatMap(_.as[MessageToFacebook].toOption))
    } getOrElse Xor.Left(DecodingFailure("This JSON is not an array!", c.history))
  }

  def loadFile(path: String): String = fromFile(path).mkString

  def loadJson(path: String): Json = parse(loadFile(path)).getOrElse(sys.error(s"Error parsing $path"))

  def decodeFromFile[T : Decoder](path: String): T = loadJson(path).as[T].getOrElse(sys.error(s"Error decoding $path"))
}
