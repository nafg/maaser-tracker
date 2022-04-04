package maasertracker.server

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits.{catsSyntaxApplicativeError, toSemigroupKOps}
import com.plaid.client.model.{ItemRemoveRequest, Products}
import io.circe.syntax.*
import maasertracker.generated.models.{PlaidInstitutionRow, PlaidItemRow}
import maasertracker.generated.tables.SlickProfile.api.*
import maasertracker.generated.tables.Tables
import maasertracker.{AddItemRequest, Institution}
import org.flywaydb.core.Flyway
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.circe.CirceEntityCodec.{circeEntityDecoder, circeEntityEncoder}
import org.http4s.circe.jsonEncoder
import org.http4s.dsl.io.*
import org.http4s.implicits.*
import org.http4s.server.Router
import org.http4s.server.middleware.Logger
import org.http4s.server.staticcontent.*
import org.http4s.{HttpRoutes, Response}
import slick.jdbc.DataSourceJdbcDataSource
import slick.jdbc.hikaricp.HikariCPJdbcDataSource

import scala.concurrent.ExecutionContext.Implicits.global

object PlaidHttp4sServer extends IOApp {
  case class ResponseFailed(errorBody: okhttp3.Response) extends RuntimeException

  private def loadItems =
    Tables.PlaidItemTable.Q.join(Tables.PlaidInstitutionTable.Q).on(_.institution === _.lookup).result
      .map(_.map { case (item, institution) =>
        PlaidItem(
          itemId = item.value.itemId,
          accessToken = item.value.accessToken,
          institution = Institution(name = institution.value.name, institution_id = institution.value.institutionId)
        )
      })

  def httpApp(plaidService: PlaidService) = {
    import plaidService.createLinkToken

    val router = Router(
      "app" -> resourceServiceBuilder[IO]("public").toRoutes
    )

    def withItem(itemId: String)(f: PlaidItemRow => IO[Response[IO]]): IO[Response[IO]] =
      Tables.PlaidItemTable.Q.filter(_.itemId === itemId).result.headOption.toIO.flatMap {
        case Some(item) => f(item.value)
        case None       => NotFound()
      }

    def createLinkTokenRequest = createLinkToken(List(Products.AUTH, Products.TRANSACTIONS))

    val routes = HttpRoutes.of[IO] {
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
          res            <- Ok()
        } yield res)
          .handleErrorWith(t => InternalServerError(t.getLocalizedMessage))
      case GET -> Root / "transactions"           =>
        for {
          items            <- loadItems.toIO
          transactionsInfo <- plaidService.transactionsInfo(items)
          res              <- Ok(transactionsInfo)
        } yield res
    }

    (router <+> routes).orNotFound
  }

  def app =
    BlazeServerBuilder[IO]
      .bindHttp(9090, "0.0.0.0")
      .withHttpApp(
        Logger.httpApp(logHeaders = true, logBody = false)(
          httpApp(new PlaidService(plaidApi))
        )
      )
      .resource

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
