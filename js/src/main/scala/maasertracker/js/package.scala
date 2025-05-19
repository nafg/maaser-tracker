package maasertracker

import scala.concurrent.Future
import scala.reflect.ClassTag
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.|

import japgolly.scalajs.react.AsyncCallback
import japgolly.scalajs.react.extra.Ajax

import io.circe.syntax.EncoderOps
import io.circe.{Decoder, Encoder}

package object js {
  def ajaxGet[A: Decoder](endpoint: String): AsyncCallback[A] =
    Ajax
      .get(endpoint)
      .send
      .asAsyncCallback
      .map(xhr => io.circe.parser.decode[A](xhr.responseText))
      .flatMap(result => AsyncCallback.fromFuture(Future.fromTry(result.toTry)))

  class AjaxPost[B] {
    def apply[A: Encoder](endpoint: String, data: A)(implicit B: Decoder[B]): AsyncCallback[B] =
      Ajax
        .post(endpoint)
        .send(data.asJson.spaces4)
        .asAsyncCallback
        .map(xhr => io.circe.parser.decode[B](xhr.responseText))
        .flatMap(result => AsyncCallback.fromFuture(Future.fromTry(result.toTry)))
  }

  def ajaxPost[B] = new AjaxPost[B]

  //noinspection ScalaUnusedSymbol
  def nullableToOption[A: ClassTag](value: A | Null): Option[A] =
    value match {
      case value: A               => Some(value)
      case value if value == null => None
    }
}
