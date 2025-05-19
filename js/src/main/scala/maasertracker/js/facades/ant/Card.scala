package maasertracker.js.facades.ant

import japgolly.scalajs.react.vdom.html_<^.*
import io.github.nafg.antd.facade.antd.libCardMod.CardSize
import io.github.nafg.antd.facade.antd.{antdStrings, components as A}

object Card {
  sealed abstract class Size(val repr: CardSize)
  object Size {
    case object Default extends Size(antdStrings.default)
    case object Small   extends Size(antdStrings.small)
  }

  def apply(size: Size = Size.Default)(content: TagMod*) =
    A.Card
      .apply(content*)
      .size(size.repr)
}
