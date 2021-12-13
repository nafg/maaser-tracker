package maasertracker

import cats.effect.IO
import com.plaid.client.ApiClient
import com.plaid.client.request.PlaidApi
import maasertracker.server.PlaidHttp4sServer.ResponseFailed
import retrofit2.{Call, Callback, Response}

import scala.jdk.CollectionConverters.*
import scala.util.chaining.scalaUtilChainingOps

package object server {
  val configRepo = new JsonRepo[Config]("config")

  lazy val plaidApi =
    configRepo.load
      .map { config =>
        new ApiClient(Map("clientId" -> config.clientId, "secret" -> config.clientSecret).asJava)
          .tap(_.setPlaidAdapter(ApiClient.Development))
          .createService(classOf[PlaidApi])
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
