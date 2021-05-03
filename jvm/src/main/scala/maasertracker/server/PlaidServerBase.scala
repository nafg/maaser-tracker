package maasertracker.server

import com.plaid.client.request.{LinkTokenCreateRequest, TransactionsGetRequest}
import com.plaid.client.response.TransactionsGetResponse
import maasertracker._
import retrofit2.Response

import java.time.{Instant, LocalDate, ZoneId}
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters._

trait PlaidServerBase {
  val transactionsMap = new ConcurrentHashMap[PlaidItem, retrofit2.Response[TransactionsGetResponse]]().asScala

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

  protected def transactions(item: PlaidItem, startDate: LocalDate): Response[TransactionsGetResponse] =
    transactionsMap.getOrElseUpdate(
      item, {
        println("Getting transactions for " + item)
        val now                    = Instant.now()
        val start                  = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant
        val transactionsGetRequest =
          new TransactionsGetRequest(
            item.accessToken,
            Date.from(start),
            Date.from(now)
          ).withCount(500)
        val res                    = plaidService.transactionsGet(transactionsGetRequest).execute()
        if (res.isSuccessful) {
          val resBody       = res.body()
          val transactions1 = Option(resBody.getTransactions).map(_.asScala).getOrElse(Nil)
          println("Got " + resBody.getTotalTransactions + s" (${transactions1.length})")
        } else {
          println("Error for " + item)
          println(res.errorBody().string())
        }
        res
      }
    )

  protected def transactionsInfo = {
    val config = configRepo()

    val startDate = config.knownMaaserBalances.lastKey
    val data      =
      for (item <- itemsRepo())
        yield {
          val resp = transactions(item, startDate)
          if (!resp.isSuccessful)
            (Nil, Nil)
          else {
            val res          = resp.body()
            val accounts     = res.getAccounts.asScala
            val transactions = res.getTransactions.asScala
            (accounts.map(item -> _), transactions)
          }
        }

    val (accounts, txs) = data.unzip

    val result =
      TransactionsInfo(
        accounts =
          accounts
            .flatten
            .map { case (item, acct) =>
              acct.getAccountId ->
                AccountInfo(institution = item.institution, account = Account(acct.getName, acct.getSubtype))
            }
            .toMap,
        transactions =
          txs
            .flatten
            .sortBy(_.getDate)
            .reverse
            .map { tx =>
              Transaction(
                accountId = tx.getAccountId,
                transactionId = tx.getTransactionId,
                name = tx.getName,
                amount = tx.getAmount,
                transactionType = tx.getTransactionType,
                category = Option(tx.getCategory).map(_.asScala.toList).getOrElse(Nil),
                date = LocalDate.parse(tx.getDate)
              )
            }
            .dropWhile(_.date.isBefore(startDate))
            .map(Right(_)),
        startingMaaserBalance = config.knownMaaserBalances.last._2,
        maaserPaymentMatchers = config.maaserPaymentMatchers,
        nonMaaserIncomeMatchers = config.nonMaaserIncomeMatchers
      )
        .removeTransfers

    result
  }
}
