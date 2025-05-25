package maasertracker

import java.time.LocalDate

import slick.additions.entity.{EntityKey, KeyedEntity}

import io.circe.Codec
import io.circe.generic.JsonCodec
import maasertracker.Codecs.{decodeKeyedEntity, encodeKeyedEntity, encodeMaybeKindKey, decodeMaybeKindKey}
import maasertracker.generated.models.MatchRuleRow

@JsonCodec
case class PlaidError(error_type: String, error_code: String, error_message: String)

@JsonCodec
case class Institution(name: String, institution_id: String)

@JsonCodec
case class PlaidItem(itemId: String, institution: Institution)

@JsonCodec
case class AddItemRequest(publicToken: String, institution: Institution)

@JsonCodec
case class Account(name: String, subtype: String)

@JsonCodec
case class AccountInfo(id: String, institution: Institution, account: Account)

@JsonCodec
case class AccountInfos(accounts: Seq[(PlaidItem, Seq[AccountInfo])]) {
  lazy val byId: Map[String, AccountInfo] = accounts.flatMap(_._2.map(info => info.id -> info)).toMap
  lazy val items                          = accounts.map(_._1)
}

@JsonCodec
case class Transaction(accountId: String,
                       transactionId: String,
                       name: String,
                       amount: Double,
                       category: List[String],
                       transactionType: String,
                       pending: Boolean,
                       date: LocalDate)

object Tags extends Enumeration {
  val Income, Exempted, Maaser = Value
}

sealed abstract class Kind(val name: String)
object Kind {
  case object Transfer    extends Kind("transfer")
  case object Income      extends Kind("income")
  case object Exemption   extends Kind("exemption")
  case object Fulfillment extends Kind("fulfillment")

  val values = List(Transfer, Income, Exemption, Fulfillment)

  def withName(name: String): Option[Kind] = values.find(_.name == name)

  def forTag(tag: Tags.Value): Kind =
    tag match {
      case Tags.Income   => Income
      case Tags.Exempted => Exemption
      case Tags.Maaser   => Fulfillment
    }
}

@JsonCodec
case class Transfer(withdrawal: Transaction, deposit: Transaction) {
  def toSeq = Seq(withdrawal, deposit)
}

@JsonCodec
case class TransactionMatcher(transactionId: Option[String],
                              institution: Option[String],
                              description: Option[String],
                              category: Option[Seq[String]],
                              minAmount: Option[BigDecimal],
                              maxAmount: Option[BigDecimal])
object TransactionMatcher {
  type Lookup = slick.additions.entity.Lookup[Long, TransactionMatcher]
  type Key    = EntityKey[Long, TransactionMatcher]
  type KEnt   = KeyedEntity[Long, TransactionMatcher]

  def fromRow(row: MatchRuleRow): TransactionMatcher =
    TransactionMatcher(
      transactionId = row.isTransactionId,
      institution = row.isInstitution,
      description = row.isDescription,
      category = row.isCategory.map(_.linesIterator.toSeq),
      minAmount = row.minAmount,
      maxAmount = row.maxAmount
    )

  def toRow(kind: String, matcher: TransactionMatcher): MatchRuleRow =
    MatchRuleRow(
      kind = kind,
      isTransactionId = matcher.transactionId,
      isDescription = matcher.description,
      isInstitution = matcher.institution,
      isCategory = matcher.category.map(_.mkString("\n")),
      minAmount = matcher.minAmount,
      maxAmount = matcher.maxAmount
    )
}

@JsonCodec
case class Matchers(byKind: Map[Option[Kind], Seq[TransactionMatcher.KEnt]]) {
  lazy val transfer        = byKind.getOrElse(Some(Kind.Transfer), Nil)
  lazy val income          = byKind.getOrElse(Some(Kind.Income), Nil)
  lazy val nonMaaserIncome = byKind.getOrElse(Some(Kind.Exemption), Nil)
  lazy val maaserPayment   = byKind.getOrElse(Some(Kind.Fulfillment), Nil)

  lazy val byId: Map[TransactionMatcher.Key, (Option[Kind], TransactionMatcher.KEnt)] =
    byKind.flatMap {
      case (kind, matchers) => matchers.map(m => m.toEntityKey -> (kind, m)).toMap
    }
}

@JsonCodec
case class PlaidData(accounts: AccountInfos,
                     transactions: Seq[Transaction],
                     startingMaaserBalance: Double,
                     errors: Map[String, Seq[PlaidError]])
object PlaidData {
  type Item = Either[Transfer, Transaction]

  implicit def transferOrTransaction: Codec.AsObject[Item] =
    Codec.codecForEither[Transfer, Transaction]("transfer", "transaction")
}
