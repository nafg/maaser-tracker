package maasertracker

import cats.effect.IO
import com.plaid.client.PlaidClient
import maasertracker.server.PlaidHttp4sServer.ResponseFailed
import retrofit2.{Call, Callback, Response}

package object server {
  val configRepo = new JsonRepo[Config]("config")

  lazy val plaidService =
    configRepo.load
      .map { config =>
        val plaid =
          PlaidClient
            .newBuilder()
            .clientIdAndSecret(config.clientId, config.clientSecret)
            .developmentBaseUrl()
            .build()
        plaid.service()
      }

  val itemsRepo = new JsonRepo[List[PlaidItem]]("items")

  def asyncResponse[A](call: Call[A]): IO[Response[A]] =
    IO.async_ { cb =>
      call.enqueue(new Callback[A] {
        override def onResponse(call: Call[A], response: Response[A]): Unit = cb(Right(response))

        override def onFailure(call: Call[A], t: Throwable): Unit = cb(Left(t))
      })
    }

  def callAsync[A](call: Call[A]): IO[A] =
    asyncResponse(call).flatMap { response =>
      if (response.isSuccessful)
        IO.pure(response.body())
      else
        IO.raiseError(ResponseFailed(response.raw()))
    }
}
