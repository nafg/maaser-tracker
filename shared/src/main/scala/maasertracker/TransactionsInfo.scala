package maasertracker

import java.time.temporal.ChronoUnit

import scala.collection.immutable.SortedSet
import scala.math.Ordering.Implicits.seqOrdering

import cats.implicits.toFunctorOps
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class TransactionsInfo private (transactions: Transactions, matchers: Matchers) {
  private def matches(tx: Transaction, matcher: TransactionMatcher) =
    matcher.id.forall(_ == tx.transactionId) &&
      matcher.institution.forall(_ == transactions.accounts.byId(tx.accountId).institution.name) &&
      matcher.description.forall(_.trim == tx.name.trim) &&
      matcher.category.forall(tx.category.startsWith(_)) &&
      matcher.minAmount.forall(tx.amount >= _) &&
      matcher.maxAmount.forall(tx.amount <= _)

  def copy(transactions: Transactions = transactions, matchers: Matchers = matchers) =
    new TransactionsInfo(transactions, matchers)

  private def combineTransfers = {
    def canBeTransfer(tx: Transaction) = matchers.transfer.exists(matches(tx, _))

    def isTransferPair(a: Transaction, b: Transaction) =
      canBeTransfer(a) && canBeTransfer(b) &&
        a.accountId != b.accountId &&
        a.amount == -b.amount &&
        ChronoUnit.DAYS.between(a.date, b.date).abs < 7

    def loop(txs: List[Transactions.Item]): List[Transactions.Item] =
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

          def loop2(as2: List[Transactions.Item]): (Transactions.Item, List[Transactions.Item]) =
            as2 match {
              case Nil                                    => Right(a)       -> Nil
              case Right(TransferMatch(transfer)) :: as22 => Left(transfer) -> as22
              case a2 :: as22                             => loop2(as22).map(a2 :: _)
            }

          val (x, xx) = loop2(as)
          x :: loop(xx)
      }

    val removed = loop(transactions.items.toList)
    copy(transactions = transactions.copy(items = removed))
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
    transactions.items
      .collect {
        case Right(AsMaaserPayment(tx, matcher))  => (tx.transactionId, (Tags.Maaser, matcher))
        case Right(AsExemptedIncome(tx, matcher)) => (tx.transactionId, (Tags.Exempted, matcher))
        case Right(AsIncome(tx, matcher))         => (tx.transactionId, (Tags.Income, matcher))
      }
      .toMap

  lazy val tags = tagsAndMatchers.view.mapValues(_._1).toMap

  lazy val maaserBalances = {
    val maaserBalances0 =
      transactions.items.reverse.scanRight(Option.empty[String] -> transactions.startingMaaserBalance) {
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

  lazy val plaidItems = transactions.accounts.items

  lazy val categories =
    transactions.items
      .flatMap {
        case Right(tx)                => Seq(tx.category)
        case Left(Transfer(tx1, tx2)) => Seq(tx1.category, tx2.category)
      }
      .to(SortedSet)
      .toSeq

}
object TransactionsInfo {
  def apply(transactions: Transactions, matchers: Matchers): TransactionsInfo =
    new TransactionsInfo(transactions, matchers).combineTransfers

  implicit val codecTransactionsInfo: Codec[TransactionsInfo] = deriveCodec
}
