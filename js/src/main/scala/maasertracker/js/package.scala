package maasertracker

import scala.reflect.ClassTag
import scala.scalajs.js.|

package object js {
  //noinspection ScalaUnusedSymbol
  def nullableToOption[A: ClassTag](value: A | Null): Option[A] =
    value match {
      case value: A               => Some(value)
      case value if value == null => None
    }
}
