package maasertracker.server

import cats.effect.IO
import cats.implicits.toTraverseOps
import com.plaid.client.PlaidApiService
import com.plaid.client.request.{ItemPublicTokenExchangeRequest, LinkTokenCreateRequest, TransactionsGetRequest}
import com.plaid.client.response.{TransactionsGetResponse, Account as PlaidAccount}
import io.circe.parser
import maasertracker.{PlaidItem as _, _}
import retrofit2.Response

import java.time.{Instant, LocalDate, ZoneId}
import java.util.Date
import scala.jdk.CollectionConverters.*

final class PlaidService(plaidApiService: PlaidApiService) {
  def createLinkToken(products: Seq[String], mod: LinkTokenCreateRequest => LinkTokenCreateRequest = identity) =
    plaidApiService.linkTokenCreate(
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

  def addItem(addItemRequest: AddItemRequest): IO[PlaidItem] =
    callAsync(plaidApiService.itemPublicTokenExchange(new ItemPublicTokenExchangeRequest(addItemRequest.publicToken)))
      .map { plaidResponse =>
        PlaidItem(
          itemId = plaidResponse.getItemId,
          accessToken = plaidResponse.getAccessToken,
          institution = addItemRequest.institution
        )
      }

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

  private def getTransactions(item: PlaidItem, start: Instant, end: Instant) = {
    val pageSize               = 500
    val transactionsGetRequest =
      new TransactionsGetRequest(item.accessToken, Date.from(start), Date.from(end))
        .withCount(pageSize)

    def loop(responses: Seq[Response[TransactionsGetResponse]]): IO[Seq[Response[TransactionsGetResponse]]] = {
      lazy val totalSoFar = responses.map(_.body.getTransactions.size()).sum

      def hasMore = totalSoFar < responses.lastOption.fold(Int.MaxValue)(_.body().getTotalTransactions)

      if (responses.forall(_.isSuccessful) && hasMore) {
        val request = transactionsGetRequest.withOffset(totalSoFar)
        println(s"withOffset($totalSoFar)")
        asyncResponse(plaidApiService.transactionsGet(request))
          .flatMap { response =>
            loop(responses :+ response)
          }
      } else
        IO.pure(responses)
    }

    loop(Nil)
  }

  def transactionsInfo(config: Config, items: List[PlaidItem]) = {
    val startDate = config.knownMaaserBalances.lastKey

    def fetch(item: PlaidItem): IO[Either[(PlaidItem, Seq[PlaidError]), (Seq[AccountInfo], Seq[Transaction])]] = {
      println("Getting transactions for " + item + " since " + startDate)

      val now = Instant.now()

      val start =
        startDate
          .atStartOfDay(ZoneId.systemDefault())
          .toInstant

      getTransactions(item, start, now)
        .map { results =>
          val (errors, successes) =
            results.partitionMap(res => if (res.isSuccessful) Right(res.body()) else Left(res.errorBody()))
          if (errors.nonEmpty)
            Left(item -> errors.map(errorBody => parser.decode[PlaidError](errorBody.string()).toTry.get))
          else {
            val accounts      =
              successes.flatMap(_.getAccounts.asScala).distinctBy(_.getAccountId).map(mkAccountInfo(item, _))
            val transactions  = successes.flatMap(_.getTransactions.asScala).map(mkTransaction)
            val returnedTxIds = transactions.map(_.transactionId).toSet
            println(s"Received ${returnedTxIds.size} transactions for ${item.institution.name}")
            Right((accounts, transactions))
          }
        }
    }

    items.traverse(fetch).map { data =>
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
          errors = errors.toMap.map { case (k, v) => k.itemId -> v }
        )
          .combineTransfers

      result
    }
  }
}
