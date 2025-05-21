package maasertracker

import cats.effect.IO
import maasertracker.generated.tables.SlickProfile.api.*
import maasertracker.server.PlaidHttp4sServer.ResponseFailed
import retrofit2.{Call, Callback, Response}

package object server {
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

  val database = Database.forConfig("db.slick")

  implicit class dbio_toIO[A](private val self: DBIO[A]) extends AnyVal {
    def toIO = IO.fromFuture(IO(database.run(self)))
  }
}
