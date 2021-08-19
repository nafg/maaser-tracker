package maasertracker.server

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits.{catsSyntaxApplicativeError, toSemigroupKOps}
import com.plaid.client.request.ItemPublicTokenExchangeRequest
import io.circe.syntax.*
import maasertracker.AddItemRequest
import org.http4s.HttpRoutes
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import org.http4s.circe.jsonEncoder
import org.http4s.dsl.io.*
import org.http4s.implicits.*
import org.http4s.server.Router
import org.http4s.server.middleware.Logger
import org.http4s.server.staticcontent.*
import retrofit2.{Call, Callback, Response}

import scala.concurrent.ExecutionContext.global

object PlaidHttp4sServer extends IOApp with PlaidServerBase {

  case class ResponseFailed(errorBody: okhttp3.Response) extends RuntimeException

  def callAsync[A](call: Call[A]): IO[A] =
    IO.async_ { cb =>
      call.enqueue(new Callback[A] {
        override def onResponse(call: Call[A], response: Response[A]): Unit =
          if (response.isSuccessful)
            cb(Right(response.body()))
          else
            cb(Left(ResponseFailed(response.raw())))

        override def onFailure(call: Call[A], t: Throwable): Unit = cb(Left(t))
      })
    }

  def httpApp = {
    val router = Router(
      "app" -> resourceServiceBuilder[IO]("public").toRoutes
    )

    object IdOfItem {
      def unapply(itemId: String) = itemsRepo().find(_.itemId == itemId)
    }

    val routes = HttpRoutes.of[IO] {
      case GET -> Root / "plaid-link-token.jsonp"     =>
        callAsync(createLinkToken(List("auth", "transactions")))
          .flatMap(res => Ok(s"const plaidLinkToken = '${res.getLinkToken}'"))
      case GET -> Root / "plaid-link-token"           =>
        callAsync(createLinkToken(List("auth", "transactions")))
          .flatMap(res => Ok(res.getLinkToken.asJson))
      case GET -> Root / "linkToken" / IdOfItem(item) =>
        callAsync(createLinkToken(Nil, _.withAccessToken(item.accessToken)))
          .flatMap(res => Ok(res.getLinkToken.asJson))
          .recoverWith { case ResponseFailed(eb) => BadRequest(eb.toString) }
      case GET -> Root / "items"                      =>
        IO.blocking(itemsRepo()).flatMap(items => Ok(items.map(_.toShared).asJson))
      case req @ POST -> Root / "items"               =>
        for {
          addItemRequest <- req.as[AddItemRequest]
          itemPublicTokenExchangeRequest = new ItemPublicTokenExchangeRequest(addItemRequest.publicToken)
          res <-
            callAsync(plaidService.itemPublicTokenExchange(itemPublicTokenExchangeRequest))
              .flatMap { plaidResponse =>
                IO.blocking(
                  itemsRepo.modify { items =>
                    items :+
                      PlaidItem(
                        itemId = plaidResponse.getItemId,
                        accessToken = plaidResponse.getAccessToken,
                        institution = addItemRequest.institution
                      )
                  }
                ) *>
                  Ok()
              }
              .handleErrorWith(t => InternalServerError(t.getLocalizedMessage))
        } yield res
      case GET -> Root / "transactions"               =>
        IO.blocking(transactionsInfo).flatMap(transactionsInfo => Ok(transactionsInfo.asJson))
    }

    (router <+> routes).orNotFound
  }

  def app =
    BlazeServerBuilder[IO](global)
      .bindHttp(9090, "0.0.0.0")
      .withHttpApp(Logger.httpApp(logHeaders = true, logBody = false)(httpApp))
      .resource

  def run(args: List[String]) = app.use(_ => IO.never).as(ExitCode.Success)
}
