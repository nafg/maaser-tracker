package maasertracker.server

import java.util

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.chaining.scalaUtilChainingOps

import slick.jdbc.DataSourceJdbcDataSource
import slick.jdbc.hikaricp.HikariCPJdbcDataSource

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits.toSemigroupKOps
import com.comcast.ip4s.IpLiteralSyntax
import com.plaid.client.ApiClient
import com.plaid.client.model.{ItemRemoveRequest, Products}
import com.plaid.client.request.PlaidApi
import com.typesafe.config.ConfigFactory
import io.circe.syntax.*
import maasertracker.Codecs.decodeEntityKey
import maasertracker.generated.models.{PlaidInstitutionRow, PlaidItemRow}
import maasertracker.generated.tables.SlickProfile.api.*
import maasertracker.generated.tables.Tables
import maasertracker.{AddItemRequest, Institution, TransactionMatcher}
import org.flywaydb.core.Flyway
import org.http4s.circe.CirceEntityCodec.{circeEntityDecoder, circeEntityEncoder}
import org.http4s.circe.jsonEncoder
import org.http4s.dsl.io.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits.*
import org.http4s.server.Router
import org.http4s.server.middleware.Logger
import org.http4s.server.staticcontent.*
import org.http4s.{HttpRoutes, Response}

object PlaidHttp4sServer extends IOApp {
  case class ResponseFailed(errorBody: okhttp3.Response) extends RuntimeException

  private def plaidRoutes(plaidService: PlaidService) = {
    import plaidService.createLinkToken

    def loadItems =
      Tables.PlaidItemTable.Q.join(Tables.PlaidInstitutionTable.Q).on(_.institution === _.lookup).result
        .map(_.map { case (item, institution) =>
          PlaidItem(
            itemId = item.value.itemId,
            accessToken = item.value.accessToken,
            institution = Institution(name = institution.value.name, institution_id = institution.value.institutionId)
          )
        })

    def withItem(itemId: String)(f: PlaidItemRow => IO[Response[IO]]): IO[Response[IO]] =
      Tables.PlaidItemTable.Q.filter(_.itemId === itemId).result.headOption.toIO.flatMap {
        case Some(item) => f(item.value)
        case None       => NotFound()
      }

    def createLinkTokenRequest = createLinkToken(List(Products.TRANSACTIONS))

    HttpRoutes.of[IO] {
      case GET -> Root / "plaid-link-token.jsonp" =>
        callAsync(createLinkTokenRequest)
          .flatMap(res => Ok(s"const plaidLinkToken = '${res.getLinkToken}'"))
      case GET -> Root / "plaid-link-token"       =>
        callAsync(createLinkTokenRequest)
          .flatMap(res => Ok(res.getLinkToken.asJson))
      case GET -> Root / "linkToken" / itemId     =>
        withItem(itemId) { item =>
          callAsync(createLinkToken(Nil, _.accessToken(item.accessToken)))
            .flatMap(res => Ok(res.getLinkToken.asJson))
            .recoverWith { case ResponseFailed(eb) => BadRequest(eb.toString) }
        }
      case GET -> Root / "items"                  =>
        loadItems
          .toIO
          .flatMap(results => Ok(results.map(_.toShared)))
      case DELETE -> Root / "items" / itemId      =>
        withItem(itemId) { item =>
          callAsync(plaidService.plaidApi.itemRemove(new ItemRemoveRequest().accessToken(item.accessToken))) >>
            Tables.PlaidItemTable.Q.filter(_.itemId === itemId).delete.toIO >>
            Ok()
        }
      case req @ POST -> Root / "items"           =>
        def getOrInsert[A](table: EntityTableModule[Long, A])(compare: (table.Row, A) => Rep[Boolean])(a: A) =
          table.Q
            .filter(compare(_, a))
            .forUpdate
            .result
            .headOption
            .flatMap {
              case Some(res) => DBIO.successful(res)
              case None      => table.Q.insert(a)
            }
            .transactionally

        (for {
          addItemRequest <- req.as[AddItemRequest]
          item           <- plaidService.addItem(addItemRequest)
          institution    <-
            getOrInsert(Tables.PlaidInstitutionTable)(_.institutionId === _.institutionId)(
              PlaidInstitutionRow(
                institutionId = item.institution.institution_id,
                name = item.institution.name
              )
            ).toIO
          _              <-
            getOrInsert(Tables.PlaidItemTable)(_.accessToken === _.accessToken)(
              PlaidItemRow(itemId = item.itemId, accessToken = item.accessToken, institution = institution)
            ).toIO
          res            <- Ok(())
        } yield res)
          .handleErrorWith { t =>
            t.printStackTrace()
            InternalServerError(t.getLocalizedMessage)
          }
      case GET -> Root / "transactions"           =>
        for {
          items        <- loadItems.toIO
          transactions <- plaidService.loadTransactions(items)
          res          <- Ok(transactions)
        } yield res
    }
  }

  private def httpApp(plaidService: PlaidService) = {
    val router = Router(
      "app" -> resourceServiceBuilder[IO]("public").toRoutes
    )

    val matchRuleRoutes = HttpRoutes.of[IO] {
      case GET -> Root / "match-rules"                          =>
        for {
          rules <- MatchRulesService.load
          res   <- Ok(rules.matchers)
        } yield res
      case req @ POST -> Root / "match-rules" / kind / "add"    =>
        for {
          matcher <- req.as[TransactionMatcher]
          row = TransactionMatcher.toRow(kind, matcher)
          _   <- Tables.MatchRuleTable.Q.insert(row).toIO
          res <- Ok(())
        } yield res
      case req @ POST -> Root / "match-rules" / kind / "delete" =>
        for {
          matcher <- req.as[TransactionMatcher.Key]
          _       <- MatchRulesService.delete(kind, matcher).toIO
          res     <- Ok(().asJson)
        } yield res
    }

    (router <+> plaidRoutes(plaidService) <+> matchRuleRoutes)
      .orNotFound
  }

  lazy val plaidApi = {
    val config = ConfigFactory.defaultApplication().getConfig("plaid")
    new ApiClient(util.Map.of("clientId", config.getString("clientId"), "secret", config.getString("secret")))
      .tap(_.setPlaidAdapter(ApiClient.Production))
      .createService(classOf[PlaidApi])
  }
  private def app =
    EmberServerBuilder.default[IO]
      .withHost(host"0.0.0.0")
      .withPort(port"9090")
      .withErrorHandler {
        case t =>
          t.printStackTrace()
          InternalServerError(t.getLocalizedMessage)
      }
      .withHttpApp(
        Logger.httpApp(logHeaders = true, logBody = false)(
          httpApp(new PlaidService(plaidApi))
        )
      )
      .build

  def run(args: List[String]) =
    IO {
      Flyway.configure()
        .dataSource(database.source match {
          case source: DataSourceJdbcDataSource => source.ds
          case source: HikariCPJdbcDataSource   => source.ds
          case source                           => sys.error("Cannot determine datasource for Flyway from " + source)
        })
        .load()
        .migrate()
    } >>
      app.useForever.as(ExitCode.Success)
}
