package maasertracker.js

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

import org.scalajs.dom.XMLHttpRequest
import japgolly.scalajs.react.{AsyncCallback, extra}

import io.circe.syntax.EncoderOps
import io.circe.{Decoder, Encoder}

object Ajax {
  def get[A: Decoder](endpoint: String): AsyncCallback[A] =
    extra.Ajax
      .get(endpoint)
      .send
      .asAsyncCallback
      .map(xhr => io.circe.parser.decode[A](xhr.responseText))
      .flatMap(result => AsyncCallback.fromFuture(Future.fromTry(result.toTry)))

  class post[B] {
    def apply[A: Encoder](endpoint: String, data: A)(implicit B: Decoder[B]): AsyncCallback[B] =
      extra.Ajax
        .post(endpoint)
        .send(data.asJson.spaces4)
        .asAsyncCallback
        .map(xhr => io.circe.parser.decode[B](xhr.responseText))
        .flatMap(result => AsyncCallback.fromFuture(Future.fromTry(result.toTry)))
  }

  def post[B] = new post[B]

  def delete(endpoint: String): AsyncCallback[Unit] =
    extra.Ajax("DELETE", endpoint)
      .send
      .asAsyncCallback
      .voidExplicit[XMLHttpRequest]
}
