package maasertracker.server

import com.plaid.client.request.{LinkTokenCreateRequest, TransactionsGetRequest}
import com.plaid.client.response.{TransactionsGetResponse, Account => PlaidAccount}
import maasertracker._
import retrofit2.Response

import java.time.{Instant, LocalDate, ZoneId}
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import scala.annotation.tailrec
import scala.jdk.CollectionConverters._

trait PlaidServerBase {
  val transactionsMap =
    new ConcurrentHashMap[PlaidItem, (Iterable[AccountInfo], Iterable[Transaction])]().asScala

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
    val data      =
      for (item <- itemsRepo())
        yield {
          val maybeResp =
            transactionsMap.updateWith(item) {
              case Some(x) => Some(x)
              case None    =>
                val accountsRepo                              =
                  new JsonRepo[Iterable[AccountInfo]](s"${item.institution.institution_id}/accounts")
                val transactionsRepo                          =
                  new JsonRepo[Iterable[Transaction]](s"${item.institution.institution_id}/transactions")
                val cachedTransactions: Iterable[Transaction] =
                  if (transactionsRepo.exists)
                    transactionsRepo()
                  else
                    Nil
                val latestCached                              = cachedTransactions.map(_.date).maxOption
                val now                                       = Instant.now()
                val start                                     =
                  latestCached.getOrElse(LocalDate.of(2000, 1, 1))
                    .minusDays(14)
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant
                println("Getting transactions for " + item + " since " + start)

                def getTransactions: Seq[Response[TransactionsGetResponse]] = {
                  val pageSize               = 500
                  val transactionsGetRequest =
                    new TransactionsGetRequest(item.accessToken, Date.from(start), Date.from(now))
                      .withCount(pageSize)

                  @tailrec
                  def loop(responses: Seq[Response[TransactionsGetResponse]])
                      : Seq[Response[TransactionsGetResponse]] = {
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
                if (errors.nonEmpty) {
                  println("Errors for " + item)
                  errors.foreach(errorBody => println(errorBody.string()))
                  None
                } else {
                  val returnedAccounts     =
                    successes.flatMap(_.getAccounts.asScala).distinctBy(_.getAccountId).map(mkAccountInfo(item, _))
                  val returnedTransactions = successes.flatMap(_.getTransactions.asScala).map(mkTransaction)
                  val returnedTxIds        = returnedTransactions.map(_.transactionId).toSet
                  val returnedAccIds       = returnedAccounts.map(_.id).toSet
                  val transactions         =
                    returnedTransactions ++
                      cachedTransactions.filterNot(tx => returnedTxIds.contains(tx.transactionId))
                  val cachedAccounts       = if (accountsRepo.exists) accountsRepo() else Nil
                  val accounts             =
                    returnedAccounts ++ cachedAccounts.filterNot(acc => returnedAccIds.contains(acc.id))
                  accountsRepo() = accounts
                  transactionsRepo() = transactions
                  Some((accounts, transactions))
                }
            }
          maybeResp.getOrElse((Nil, Nil))
        }

    val (accounts, txs) = data.unzip

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
        nonMaaserIncomeMatchers = config.nonMaaserIncomeMatchers
      )
        .removeTransfers

    result
  }
}
