package maasertracker.server

import io.circe.generic.JsonCodec
import io.circe.{KeyDecoder, KeyEncoder}
import maasertracker.TransactionMatcher

import java.time.LocalDate
import scala.collection.immutable.SortedMap
import scala.util.Try

@JsonCodec
case class Config(clientId: String,
                  clientSecret: String,
                  knownMaaserBalances: SortedMap[LocalDate, Double],
                  maaserPaymentMatchers: Seq[TransactionMatcher],
                  nonMaaserIncomeMatchers: Seq[TransactionMatcher])

object Config {
  implicit def localDateKeyEncoder: KeyEncoder[LocalDate] = KeyEncoder.instance(_.toString)
  implicit def localDateKeyDecoder: KeyDecoder[LocalDate] = KeyDecoder.instance(s => Try(LocalDate.parse(s)).toOption)
}
