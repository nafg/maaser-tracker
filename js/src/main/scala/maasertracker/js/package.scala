package maasertracker

import scala.reflect.ClassTag
import scala.scalajs.js
import scala.scalajs.js.|

import japgolly.scalajs.react.vdom.html_<^.*

package object js {
  def formatDollars(amount: Double)             = f"$$$amount%,.2f"
  def formatDollars(amount: BigDecimal): String = formatDollars(amount.toDouble)

  implicit class any_foldNull[A](private val self: A) extends AnyVal {
    def foldNull[B](f: A => B => A)(b: B): A                  =
      if (b == null) self
      else f(self)(b)
    def foldUndefined[B](f: A => B => A)(b: js.UndefOr[B]): A = b.fold(self)(f(self))
  }

  def renderMatcher(matcher: TransactionMatcher) =
    (matcher.transactionId.map(id => <.strong("ID is ", id)) ++
      matcher.institution.map(inst => <.strong("Institution is ", inst)) ++
      matcher.description.map(desc => <.strong("Description is ", desc)) ++
      matcher.category.map(cat => <.strong("Category is ", cat.mkString(" > "))) ++
      matcher.minAmount.map(min => <.strong("Min Amount is ", formatDollars(min))) ++
      matcher.maxAmount.map(max => <.strong("Max Amount is ", formatDollars(max))))
      .mkReactFragment(" and ")

  //noinspection ScalaUnusedSymbol
  def nullableToOption[A: ClassTag](value: A | Null): Option[A] =
    value match {
      case value: A               => Some(value)
      case value if value == null => None
    }
}
