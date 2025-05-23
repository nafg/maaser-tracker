package maasertracker

import scala.reflect.ClassTag
import scala.scalajs.js.|

package object js {
  def formatDollars(amount: Double) = f"$$$amount%,.2f"

  implicit class any_foldNull[A](private val self: A) extends AnyVal {
    def foldNull[B](f: A => B => A)(b: B): A =
      if (b == null) self
      else f(self)(b)
  }

  //noinspection ScalaUnusedSymbol
  def nullableToOption[A: ClassTag](value: A | Null): Option[A] =
    value match {
      case value: A               => Some(value)
      case value if value == null => None
    }
}
