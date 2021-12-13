package maasertracker.server

import cats.effect.IO
import cats.implicits.toTraverseOps
import com.plaid.client.model
import com.plaid.client.model.*
import com.plaid.client.request.PlaidApi
import io.circe.parser
import maasertracker.{PlaidItem as _, *}
import retrofit2.Response

import java.time.LocalDate
import scala.jdk.CollectionConverters.*

final class PlaidService(val plaidApi: PlaidApi) {
  def createLinkToken(products: Seq[Products], mod: LinkTokenCreateRequest => LinkTokenCreateRequest = identity) =
    plaidApi.linkTokenCreate(
      mod(
        new LinkTokenCreateRequest()
          .user(new LinkTokenCreateRequestUser().clientUserId("me"))
          .clientName("Maaser Tracker")
          .products(products.asJava)
          .countryCodes(List(CountryCode.US).asJava)
          .language("en")
      )
    )

  def addItem(addItemRequest: AddItemRequest): IO[PlaidItem] =
    callAsync(
      plaidApi.itemPublicTokenExchange(new ItemPublicTokenExchangeRequest().publicToken(addItemRequest.publicToken))
    )
      .map { plaidResponse =>
        PlaidItem(
          itemId = plaidResponse.getItemId,
          accessToken = plaidResponse.getAccessToken,
          institution = addItemRequest.institution
        )
      }

  private def mkAccountInfo(item: PlaidItem, acct: AccountBase) =
    AccountInfo(
      id = acct.getAccountId,
      institution = item.institution,
      account = Account(acct.getName, acct.getSubtype.getValue)
    )

  private def mkTransaction(tx: model.Transaction) =
    maasertracker.Transaction(
      accountId = tx.getAccountId,
      transactionId = tx.getTransactionId,
      name = tx.getName,
      amount = tx.getAmount,
      transactionType = tx.getTransactionType.getValue,
      category = Option(tx.getCategory).map(_.asScala.toList).getOrElse(Nil),
      date = tx.getDate
    )

  private def getTransactions(item: PlaidItem, start: LocalDate, end: LocalDate) = {
    val pageSize               = 500
    val transactionsGetRequest =
      new TransactionsGetRequest()
        .accessToken(item.accessToken)
        .startDate(start)
        .endDate(end)

    def loop(responses: Seq[Response[TransactionsGetResponse]]): IO[Seq[Response[TransactionsGetResponse]]] = {
      lazy val totalSoFar = responses.map(_.body.getTransactions.size()).sum

      def hasMore = totalSoFar < responses.lastOption.fold(Int.MaxValue)(_.body().getTotalTransactions)

      if (responses.forall(_.isSuccessful) && hasMore) {
        val request =
          transactionsGetRequest.options(
            new TransactionsGetRequestOptions()
              .count(pageSize)
              .offset(totalSoFar)
          )
        println(s"Offset: $totalSoFar")
        asyncResponse(plaidApi.transactionsGet(request))
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

    def fetch(item: PlaidItem)
        : IO[Either[(PlaidItem, Seq[PlaidError]), (Seq[AccountInfo], Seq[maasertracker.Transaction])]] = {
      println("Getting transactions for " + item + " since " + startDate)

      val start = startDate

      getTransactions(item, start, LocalDate.now())
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
          transferMatchers = config.transferMatchers,
          errors = errors.toMap.map { case (k, v) => k.itemId -> v }
        )
          .combineTransfers

      result
    }
  }
}
