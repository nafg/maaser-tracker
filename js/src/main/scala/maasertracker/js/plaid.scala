package maasertracker.js

import scala.annotation.unused
import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal

trait PlaidCreateParam extends js.Object {
  val env: String
  val product: js.Array[String]
  val token: String
  val onSuccess: js.Function2[String, js.Dynamic, Unit]
}

trait Plaid extends js.Any {
  def open(): Unit
}

@js.native
@JSGlobal
object Plaid extends js.Object {
  def create(@unused param: PlaidCreateParam): Plaid = js.native
}
