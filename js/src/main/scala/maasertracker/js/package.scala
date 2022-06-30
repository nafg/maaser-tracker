package maasertracker

import scala.concurrent.Future
import scala.reflect.ClassTag
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.|

import japgolly.scalajs.react.AsyncCallback
import japgolly.scalajs.react.extra.Ajax

import io.circe.Decoder

package object js {
  def ajax[A: Decoder](endpoint: String) =
    Ajax
      .get(endpoint)
      .send
      .asAsyncCallback
      .map(xhr => io.circe.parser.decode[A](xhr.responseText))
      .flatMap(result => AsyncCallback.fromFuture(Future.fromTry(result.toTry)))

  def nullableToOption[A: ClassTag](value: A | Null): Option[A] =
    value match {
      case value: A               => Some(value)
      case value if value == null => None
    }
}
