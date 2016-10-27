package com.gu.facebook_news_bot.utils

import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

import com.gu.facebook_news_bot.BotConfig
import com.typesafe.scalalogging.StrictLogging
import org.apache.commons.codec.binary.Hex

/**
  * From https://developers.facebook.com/docs/messenger-platform/webhook-reference#security:
  *
  * "The HTTP request will contain an X-Hub-Signature header which contains the SHA1 signature of the request payload,
  * using the app secret as the key, and prefixed with 'sha1='."
  */
object Verification extends StrictLogging {
  val CryptoAlgorithm = "HmacSHA1"

  def verifySignature(signature: String, body: Array[Byte]): Boolean = {
    signature.split("=").lastOption.exists { expectedHash =>
      val secretKeySpec = new SecretKeySpec(BotConfig.facebook.secret.getBytes(StandardCharsets.UTF_8), CryptoAlgorithm)
      val mac = Mac.getInstance(CryptoAlgorithm)
      mac.init(secretKeySpec)
      val result = mac.doFinal(body)

      val computedHash = Hex.encodeHex(result).mkString
      computedHash == expectedHash
    }
  }
}
