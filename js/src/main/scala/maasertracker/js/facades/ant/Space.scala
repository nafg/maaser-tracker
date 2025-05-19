package maasertracker.js.facades.ant

import scala.scalajs.js.|

import japgolly.scalajs.react.vdom.html_<^.*
import io.github.nafg.antd.facade.antd.{antdStrings, components as A}

object Space {
  sealed abstract class Direction(val repr: antdStrings.horizontal | antdStrings.vertical)
  object Direction {
    case object Horizontal extends Direction(antdStrings.horizontal)
    case object Vertical   extends Direction(antdStrings.vertical)
  }

  def apply(direction: Direction = Direction.Horizontal)(content: TagMod*) =
    A.Space
      .apply(content*)
      .direction(direction.repr)
}
