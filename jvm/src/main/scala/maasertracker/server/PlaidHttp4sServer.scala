package maasertracker.server

import cats.effect.{Blocker, ExitCode, IO, IOApp}
import cats.implicits.{catsSyntaxApplicativeError, toSemigroupKOps}
import com.plaid.client.request.ItemPublicTokenExchangeRequest
import io.circe.syntax._
import maasertracker.{AddItemRequest, PlaidItem}
import org.http4s.HttpRoutes
import org.http4s.circe.CirceEntityCodec.circeEntityDecoder
import org.http4s.circe.jsonEncoder
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Logger
import org.http4s.server.staticcontent._
import retrofit2.{Call, Callback, Response}

import scala.concurrent.ExecutionContext.global

object PlaidHttp4sServer extends IOApp with PlaidServerBase {

  case class ResponseFailed(errorBody: okhttp3.Response) extends RuntimeException

  def callAsync[A](call: Call[A]): IO[A] =
    IO.async { cb =>
      call.enqueue(new Callback[A] {
        override def onResponse(call: Call[A], response: Response[A]): Unit =
          if (response.isSuccessful)
            cb(Right(response.body()))
          else
            cb(Left(ResponseFailed(response.raw())))

        override def onFailure(call: Call[A], t: Throwable): Unit = cb(Left(t))
      })
    }

  def httpApp(blocker: Blocker) = {
    def block[A](f: => A) = blocker.delay[IO, A](f)

    val router = Router(
      "app" -> resourceService[IO](ResourceService.Config("public", blocker))
    )

    object Params {
      object accessToken extends QueryParamDecoderMatcher[String]("accessToken")
    }

    val routes = HttpRoutes.of[IO] {
      case GET -> Root / "plaid-link-token.jsonp"                       =>
        callAsync(createLinkToken(List("auth", "transactions")))
          .flatMap(res => Ok(s"const plaidLinkToken = '${res.getLinkToken}'"))
      case GET -> Root / "plaid-link-token"                             =>
        callAsync(createLinkToken(List("auth", "transactions")))
          .flatMap(res => Ok(res.getLinkToken.asJson))
      case GET -> Root / "linkToken" :? Params.accessToken(accessToken) =>
        callAsync(createLinkToken(Nil, _.withAccessToken(accessToken)))
          .flatMap(res => Ok(res.getLinkToken.asJson))
          .recoverWith { case ResponseFailed(eb) =>
            println(eb.body())
            BadRequest(eb.toString)
          }
      case GET -> Root / "items"                                        =>
        block(itemsRepo()).flatMap(items => Ok(items.asJson))
      case req @ POST -> Root / "items"                                 =>
        for {
          addItemRequest                <- req.as[AddItemRequest]
          itemPublicTokenExchangeRequest = new ItemPublicTokenExchangeRequest(addItemRequest.publicToken)
          res                           <-
            callAsync(plaidService.itemPublicTokenExchange(itemPublicTokenExchangeRequest))
              .flatMap { plaidResponse =>
                block(itemsRepo.modify(_ :+ PlaidItem(plaidResponse.getAccessToken, addItemRequest.institution))) *>
                  Ok()
              }
              .handleErrorWith(t => InternalServerError(t.getLocalizedMessage))
        } yield res
      case GET -> Root / "transactions"                                 =>
        block(transactionsInfo).flatMap(transactionsInfo => Ok(transactionsInfo.asJson))
    }

    (router <+> routes).orNotFound
  }

  def app =
    for {
      blocker <- Blocker[IO]

      // With Middlewares in place
      finalHttpApp = Logger.httpApp(logHeaders = true, logBody = false)(httpApp(blocker))

      exitCode <-
        BlazeServerBuilder[IO](global)
          .bindHttp(9090, "0.0.0.0")
          .withHttpApp(finalHttpApp)
          .resource
    } yield exitCode

  def run(args: List[String]) = app.use(_ => IO.never).as(ExitCode.Success)
}
