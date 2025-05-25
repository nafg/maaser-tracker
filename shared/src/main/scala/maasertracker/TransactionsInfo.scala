package maasertracker

import java.time.temporal.ChronoUnit

import scala.collection.immutable.SortedSet
import scala.math.Ordering.Implicits.seqOrdering

import cats.implicits.toFunctorOps
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec

case class TransactionsInfo private (plaidData: PlaidData, matchers: Matchers) {
  def matches(tx: Transaction, matcher: TransactionMatcher) =
    matcher.transactionId.forall(_ == tx.transactionId) &&
      matcher.institution.forall(_ == plaidData.accounts.byId(tx.accountId).institution.name) &&
      matcher.description.forall(_.trim == tx.name.trim) &&
      matcher.category.forall(tx.category.startsWith(_)) &&
      matcher.minAmount.forall(_ <= tx.amount) &&
      matcher.maxAmount.forall(_ >= tx.amount)

  def matchersFor(tx: Transaction) =
    matchers.byKind
      .view
      .mapValues(_.filter(matcher => matches(tx, matcher.value)))
      .filter(_._2.nonEmpty)
      .toMap

  lazy val combinedItems = {
    def canBeTransfer(tx: Transaction) = matchers.transfer.exists(matcher => matches(tx, matcher.value))

    def isTransferPair(a: Transaction, b: Transaction) =
      canBeTransfer(a) && canBeTransfer(b) &&
        a.accountId != b.accountId &&
        a.amount == -b.amount &&
        ChronoUnit.DAYS.between(a.date, b.date).abs < 7

    def loop(txs: List[Transaction]): List[PlaidData.Item] =
      txs match {
        case Nil     => Nil
        case a :: as =>
          object TransferMatch {
            def unapply(b: Transaction): Option[Transfer] =
              (isTransferPair(a, b), math.signum(a.amount), math.signum(b.amount)) match {
                case (true, 1, -1) => Some(Transfer(a, b))
                case (true, -1, 1) => Some(Transfer(b, a))
                case _             => None
              }
          }

          def loop2(as2: List[Transaction]): (PlaidData.Item, List[Transaction]) =
            as2 match {
              case Nil                             => Right(a)       -> Nil
              case TransferMatch(transfer) :: as22 => Left(transfer) -> as22
              case a2 :: as22                      => loop2(as22).map(a2 :: _)
            }

          val (x, xx) = loop2(as)
          x :: loop(xx)
      }

    loop(plaidData.transactions.toList)
  }

  def copy(transactions: PlaidData = plaidData, matchers: Matchers = matchers) =
    new TransactionsInfo(transactions, matchers)

  private class MatcherExtractor(matchers: Seq[TransactionMatcher.KEnt]) {
    def unapply(tx: Transaction): Option[(Transaction, TransactionMatcher.KEnt)] =
      matchers.find(matcher => matches(tx, matcher.value)).map(tx -> _)
  }

  private object AsIncome         extends MatcherExtractor(matchers.income)
  private object AsExemptedIncome extends MatcherExtractor(matchers.nonMaaserIncome)
  private object AsMaaserPayment  extends MatcherExtractor(matchers.maaserPayment) {
    override def unapply(tx: Transaction): Option[(Transaction, TransactionMatcher.KEnt)] =
      if (tx.amount <= 0) None
      else super.unapply(tx)
  }

  lazy val tagsAndMatchers: Map[String, (Tags.Value, TransactionMatcher.KEnt)] =
    combinedItems
      .collect {
        case Right(AsMaaserPayment(tx, matcher))  => (tx.transactionId, (Tags.Maaser, matcher))
        case Right(AsExemptedIncome(tx, matcher)) => (tx.transactionId, (Tags.Exempted, matcher))
        case Right(AsIncome(tx, matcher))         => (tx.transactionId, (Tags.Income, matcher))
      }
      .toMap

  lazy val tags = tagsAndMatchers.view.mapValues(_._1).toMap

  lazy val maaserBalances = {
    val maaserBalances0 =
      combinedItems.reverse.scanRight(Option.empty[String] -> plaidData.startingMaaserBalance) {
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

  lazy val plaidItems = plaidData.accounts.items

  lazy val categories =
    plaidData.transactions
      .map(_.category)
      .to(SortedSet)
      .toSeq

}
object TransactionsInfo {
  def apply(transactions: PlaidData, matchers: Matchers): TransactionsInfo =
    new TransactionsInfo(transactions, matchers)

  implicit val codecTransactionsInfo: Codec[TransactionsInfo] = deriveCodec
}
