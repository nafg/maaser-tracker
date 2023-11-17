package maasertracker

import java.time.LocalDate
import java.time.temporal.ChronoUnit

import scala.math.Ordering.Implicits.infixOrderingOps

import cats.implicits.*
import io.circe.Codec
import io.circe.generic.JsonCodec

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

  private def isIncome(tx: Transaction) = matchers.income.exists(matches(tx, _))

  private def isExempted(tx: Transaction) = matchers.nonMaaserIncome.exists(matches(tx, _))

  private def isMaaserPayment(tx: Transaction) = tx.amount > 0 && matchers.maaserPayment.exists(matches(tx, _))

  lazy val tags =
    transactions
      .collect {
        case Right(tx) if isMaaserPayment(tx) => tx.transactionId -> Tags.Maaser
        case Right(tx) if isIncome(tx)        =>
          tx.transactionId ->
            (if (isExempted(tx)) Tags.Exempted else Tags.Income)
      }
      .toMap

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

  def sorted =
    copy(transactions = transactions.sortBy {
      case Left(Transfer(withdrawal, deposit)) => (withdrawal.date min deposit.date) -> None
      case Right(value)                        => value.date                         -> tags.get(value.transactionId)
    })

  def untilLastMaaserPayment = {
    def loop(txs: List[TransactionsInfo.Item], maaserDate: Option[LocalDate] = None): List[TransactionsInfo.Item] =
      txs match {
        case Nil            => Nil
        case Left(x) :: xs  => Left(x) :: loop(xs, None)
        case Right(x) :: xs =>
          if (maaserDate.exists(x.date < _)) Nil
          else if (isMaaserPayment(x)) loop(xs, Some(x.date))
          else Right(x) :: loop(xs, maaserDate)
      }

    copy(transactions = loop(transactions.toList))
  }
}

object TransactionsInfo {
  type Item = Either[Transfer, Transaction]

  implicit def transferOrTransaction: Codec.AsObject[Item] =
    Codec.codecForEither[Transfer, Transaction]("transfer", "transaction")
}
