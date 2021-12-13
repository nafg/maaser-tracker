package maasertracker.server

import cats.effect.{ExitCode, IO, IOApp, Resource}
import cats.implicits.{catsSyntaxApplicativeError, toSemigroupKOps}
import com.plaid.client.model.{ItemRemoveRequest, Products}
import io.circe.syntax.*
import maasertracker.AddItemRequest
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import org.http4s.circe.jsonEncoder
import org.http4s.dsl.io.*
import org.http4s.implicits.*
import org.http4s.server.Router
import org.http4s.server.middleware.Logger
import org.http4s.server.staticcontent.*
import org.http4s.{HttpRoutes, Response}

import scala.concurrent.ExecutionContext.global

object PlaidHttp4sServer extends IOApp {
  case class ResponseFailed(errorBody: okhttp3.Response) extends RuntimeException

  def httpApp(plaidService: PlaidService) = {
    import plaidService.createLinkToken

    val router = Router(
      "app" -> resourceServiceBuilder[IO]("public").toRoutes
    )

    def withItem(itemId: String)(f: PlaidItem => IO[Response[IO]]): IO[Response[IO]] =
      itemsRepo.load.flatMap { items =>
        items.find(_.itemId == itemId) match {
          case Some(item) => f(item)
          case None       => NotFound()
        }
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
        itemsRepo.load.flatMap(items => Ok(items.map(_.toShared).asJson))
      case DELETE -> Root / "items" / itemId      =>
        withItem(itemId) { item =>
          callAsync(plaidService.plaidApi.itemRemove(new ItemRemoveRequest().accessToken(item.accessToken))) >>
            itemsRepo.modify(_.filterNot(_.itemId == itemId)) *> Ok()
        }
      case req @ POST -> Root / "items"           =>
        (for {
          addItemRequest <- req.as[AddItemRequest]
          item           <- plaidService.addItem(addItemRequest)
          _              <- itemsRepo.modify(_ :+ item)
          res            <- Ok()
        } yield res)
          .handleErrorWith(t => InternalServerError(t.getLocalizedMessage))
      case GET -> Root / "transactions"           =>
        for {
          config           <- configRepo.load
          items            <- itemsRepo.load
          transactionsInfo <- plaidService.transactionsInfo(config, items)
          res              <- Ok(transactionsInfo.asJson)
        } yield res
    }

    (router <+> routes).orNotFound
  }

  def app =
    for {
      plaidApi <- Resource.eval(plaidApi)
      server   <-
        BlazeServerBuilder[IO](global)
          .bindHttp(9090, "0.0.0.0")
          .withHttpApp(
            Logger.httpApp(logHeaders = true, logBody = false)(
              httpApp(new PlaidService(plaidApi))
            )
          )
          .resource
    } yield server

  def run(args: List[String]) = app.useForever.as(ExitCode.Success)
}
