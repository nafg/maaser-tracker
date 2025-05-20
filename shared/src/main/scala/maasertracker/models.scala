package maasertracker

import java.time.LocalDate
import java.time.temporal.ChronoUnit

import cats.implicits.*
import io.circe.Codec
import io.circe.generic.JsonCodec
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
case class TransactionMatcher(id: Option[String],
                              institution: Option[String],
                              description: Option[String],
                              category: Option[Seq[String]],
                              minAmount: Option[BigDecimal],
                              maxAmount: Option[BigDecimal])
object TransactionMatcher {
  def fromRow(row: MatchRuleRow): TransactionMatcher =
    TransactionMatcher(
      id = row.isTransactionId,
      institution = row.isInstitution,
      description = row.isDescription,
      category = row.isCategory.map(_.linesIterator.toSeq),
      minAmount = row.minAmount,
      maxAmount = row.maxAmount
    )

  def toRow(kind: String, matcher: TransactionMatcher): MatchRuleRow =
    MatchRuleRow(
      kind = kind,
      isTransactionId = matcher.id,
      isDescription = matcher.description,
      isInstitution = matcher.institution,
      isCategory = matcher.category.map(_.mkString("\n")),
      minAmount = matcher.minAmount,
      maxAmount = matcher.maxAmount
    )
}

@JsonCodec
case class Matchers(transfer: Seq[TransactionMatcher],
                    income: Seq[TransactionMatcher],
                    nonMaaserIncome: Seq[TransactionMatcher],
                    maaserPayment: Seq[TransactionMatcher])

@JsonCodec
case class TransactionsInfo(accounts: Map[String, AccountInfo],
                            transactions: Seq[TransactionsInfo.Item],
                            startingMaaserBalance: Double,
                            matchers: Matchers,
                            errors: Map[String, Seq[PlaidError]]) {
  private def matches(tx: Transaction, matcher: TransactionMatcher) =
    matcher.id.forall(_ == tx.transactionId) &&
      matcher.institution.forall(_ == accounts(tx.accountId).institution.name) &&
      matcher.description.forall(_.trim == tx.name.trim) &&
      matcher.category.forall(tx.category.startsWith(_)) &&
      matcher.minAmount.forall(tx.amount >= _) &&
      matcher.maxAmount.forall(tx.amount <= _)

  def combineTransfers = {
    def canBeTransfer(tx: Transaction) = matchers.transfer.exists(matches(tx, _))

    def isTransferPair(a: Transaction, b: Transaction) =
      canBeTransfer(a) && canBeTransfer(b) &&
        a.accountId != b.accountId &&
        a.amount == -b.amount &&
        ChronoUnit.DAYS.between(a.date, b.date).abs < 7

    def loop(txs: List[TransactionsInfo.Item]): List[TransactionsInfo.Item] =
      txs match {
        case Nil            => Nil
        case Left(_) :: as  => loop(as)
        case Right(a) :: as =>
          object TransferMatch {
            def unapply(b: Transaction): Option[Transfer] =
              (isTransferPair(a, b), math.signum(a.amount), math.signum(b.amount)) match {
                case (true, 1, -1) => Some(Transfer(a, b))
                case (true, -1, 1) => Some(Transfer(b, a))
                case _             => None
              }
          }

          def loop2(as2: List[TransactionsInfo.Item]): (TransactionsInfo.Item, List[TransactionsInfo.Item]) =
            as2 match {
              case Nil                                    => Right(a)       -> Nil
              case Right(TransferMatch(transfer)) :: as22 => Left(transfer) -> as22
              case a2 :: as22                             => loop2(as22).map(a2 :: _)
            }

          val (x, xx) = loop2(as)
          x :: loop(xx)
      }

    val removed = loop(transactions.toList)
    copy(transactions = removed)
  }

  private class MatcherExtractor(matchers: Seq[TransactionMatcher]) {
    def unapply(tx: Transaction): Option[(Transaction, TransactionMatcher)] = matchers.find(matches(tx, _)).map(tx -> _)
  }

  private object AsIncome         extends MatcherExtractor(matchers.income)
  private object AsExemptedIncome extends MatcherExtractor(matchers.nonMaaserIncome)
  private object AsMaaserPayment  extends MatcherExtractor(matchers.maaserPayment) {
    override def unapply(tx: Transaction): Option[(Transaction, TransactionMatcher)] =
      if (tx.amount <= 0) None
      else super.unapply(tx)
  }

  lazy val tagsAndMatchers: Map[String, (Tags.Value, TransactionMatcher)] =
    transactions
      .collect {
        case Right(AsMaaserPayment(tx, matcher))  => (tx.transactionId, (Tags.Maaser, matcher))
        case Right(AsExemptedIncome(tx, matcher)) => (tx.transactionId, (Tags.Exempted, matcher))
        case Right(AsIncome(tx, matcher))         => (tx.transactionId, (Tags.Income, matcher))
      }
      .toMap

  lazy val tags = tagsAndMatchers.view.mapValues(_._1).toMap

  lazy val maaserBalances = {
    val maaserBalances0 =
      transactions.reverse.scanRight(Option.empty[String] -> startingMaaserBalance) {
        case (Left(_), (_, m))   => None -> m
        case (Right(tx), (_, m)) =>
          Some(tx.transactionId) ->
            (tags.get(tx.transactionId) match {
              case Some(Tags.Income) => m - tx.amount / 10
              case Some(Tags.Maaser) => m - tx.amount
              case _                 => m
            })
      }
    maaserBalances0
      .collect { case (Some(id), d) => (id, d) }
      .toMap
  }
}

object TransactionsInfo {
  type Item = Either[Transfer, Transaction]

  implicit def transferOrTransaction: Codec.AsObject[Item] =
    Codec.codecForEither[Transfer, Transaction]("transfer", "transaction")
}
