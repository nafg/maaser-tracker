package maasertracker.js

import scala.annotation.unused
import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal

import japgolly.scalajs.react.AsyncCallback

import io.circe.generic.JsonCodec
import maasertracker.Institution

trait PlaidCreateParam extends js.Object {
  val env: String
  val product: js.Array[String]
  val token: String
  val onSuccess: js.Function2[String, js.Dynamic, Unit]
}

trait PlaidFacade extends js.Any {
  def open(): Unit
}

@js.native
@JSGlobal("Plaid")
object PlaidFacade extends js.Object {
  def create(@unused param: PlaidCreateParam): PlaidFacade = js.native
}

object Plaid {
  @JsonCodec(decodeOnly = true)
  case class PlaidLinkOnSuccessMetadata(institution: Institution)

  case class PlaidLinkSuccessResult(publicToken: String, metadata: PlaidLinkOnSuccessMetadata)

  def makeAndOpen(linkToken: String) =
    AsyncCallback.promise[PlaidLinkSuccessResult]
      .asAsyncCallback
      .flatMap { case (acb, fulfill) =>
        val facade =
          PlaidFacade.create(
            new PlaidCreateParam {
              override val env       = "production"
              override val product   = js.Array("transactions")
              override val token     = linkToken
              override val onSuccess =
                js.Any.fromFunction2 { (publicToken, metadata) =>
                  val throwableOrResult =
                    io.circe.scalajs.decodeJs[PlaidLinkOnSuccessMetadata](metadata)
                      .map(metadata => PlaidLinkSuccessResult(publicToken, metadata))

                  fulfill(throwableOrResult.toTry)
                    .runNow()
                }
            }
          )

        AsyncCallback.delay(facade.open()) >>
          acb
      }
}
