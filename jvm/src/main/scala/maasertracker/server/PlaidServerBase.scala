package maasertracker.server

import com.plaid.client.request.{LinkTokenCreateRequest, TransactionsGetRequest}
import com.plaid.client.response.{TransactionsGetResponse, Account as PlaidAccount}
import io.circe.parser
import maasertracker.*
import retrofit2.Response

import java.time.{Instant, LocalDate, ZoneId}
import java.util.Date
import scala.annotation.tailrec
import scala.jdk.CollectionConverters.*

trait PlaidServerBase {
  def createLinkToken(products: Seq[String], mod: LinkTokenCreateRequest => LinkTokenCreateRequest = identity) =
    plaidService.linkTokenCreate(
      mod(
        new LinkTokenCreateRequest(
          new LinkTokenCreateRequest.User("me"),
          "Maaser Tracker",
          products.asJava,
          List("US").asJava,
          "en"
        )
      )
    )

  private def mkAccountInfo(item: PlaidItem, acct: PlaidAccount) =
    AccountInfo(
      id = acct.getAccountId,
      institution = item.institution,
      account = Account(acct.getName, acct.getSubtype)
    )

  private def mkTransaction(tx: TransactionsGetResponse.Transaction) =
    Transaction(
      accountId = tx.getAccountId,
      transactionId = tx.getTransactionId,
      name = tx.getName,
      amount = tx.getAmount,
      transactionType = tx.getTransactionType,
      category = Option(tx.getCategory).map(_.asScala.toList).getOrElse(Nil),
      date = LocalDate.parse(tx.getDate)
    )

  protected def transactionsInfo = {
    val config = configRepo()

    val startDate = config.knownMaaserBalances.lastKey

    def fetch(item: PlaidItem): Either[(PlaidItem, Seq[PlaidError]), (Seq[AccountInfo], Seq[Transaction])] = {
      println("Getting transactions for " + item + " since " + startDate)

      val now = Instant.now()

      val start =
        startDate
          .atStartOfDay(ZoneId.systemDefault())
          .toInstant

      def getTransactions: Seq[Response[TransactionsGetResponse]] = {
        val pageSize               = 500
        val transactionsGetRequest =
          new TransactionsGetRequest(item.accessToken, Date.from(start), Date.from(now))
            .withCount(pageSize)

        @tailrec
        def loop(responses: Seq[Response[TransactionsGetResponse]]): Seq[Response[TransactionsGetResponse]] = {
          lazy val totalSoFar = responses.map(_.body.getTransactions.size()).sum

          def hasMore = totalSoFar < responses.lastOption.fold(Int.MaxValue)(_.body().getTotalTransactions)

          if (responses.forall(_.isSuccessful) && hasMore) {
            val request  = transactionsGetRequest.withOffset(totalSoFar)
            println(s"withOffset($totalSoFar)")
            val response = plaidService.transactionsGet(request).execute()
            loop(responses :+ response)
          } else
            responses
        }

        loop(Nil)
      }

      val results             = getTransactions
      val (errors, successes) =
        results.partitionMap(res => if (res.isSuccessful) Right(res.body()) else Left(res.errorBody()))
      if (errors.nonEmpty)
        Left(item -> errors.map(errorBody => parser.decode[PlaidError](errorBody.string()).toTry.get))
      else {
        val accounts = successes.flatMap(_.getAccounts.asScala).distinctBy(_.getAccountId).map(mkAccountInfo(item, _))
        val transactions  = successes.flatMap(_.getTransactions.asScala).map(mkTransaction)
        val returnedTxIds = transactions.map(_.transactionId).toSet
        println(s"Received ${returnedTxIds.size} transactions for ${item.institution.name}")
        Right((accounts, transactions))
      }
    }

    val data =
      for (item <- itemsRepo())
        yield fetch(item)

    val (errors, successes) = data.partitionMap(identity)
    val (accounts, txs)     = successes.unzip

    val result =
      TransactionsInfo(
        accounts = accounts.flatten.map(account => account.id -> account).toMap,
        transactions =
          txs
            .flatten
            .sortBy(_.date)
            .dropWhile(_.date.isBefore(startDate))
            .reverse
            .map(Right(_)),
        startingMaaserBalance = config.knownMaaserBalances.last._2,
        maaserPaymentMatchers = config.maaserPaymentMatchers,
        nonMaaserIncomeMatchers = config.nonMaaserIncomeMatchers,
        errors = errors.toMap.map { case (k, v) => k.institution.institution_id -> v }
      )
        .combineTransfers

    result
  }
}
