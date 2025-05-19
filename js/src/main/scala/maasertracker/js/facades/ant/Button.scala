package maasertracker.js.facades.ant

import org.scalajs.dom.HTMLElement
import japgolly.scalajs.react.ReactMouseEventFrom
import japgolly.scalajs.react.callback.Callback
import japgolly.scalajs.react.vdom.html_<^.*
import io.github.nafg.antd.facade.antd.libButtonButtonMod.ButtonType
import io.github.nafg.antd.facade.antd.{antdStrings, components as A}

object Button {
  sealed abstract class Type(val repr: ButtonType)
  object Type {
    case object Default extends Type(antdStrings.default)
    //noinspection ScalaUnusedSymbol
    case object Dashed  extends Type(antdStrings.dashed)
    //noinspection ScalaUnusedSymbol
    case object Ghost   extends Type(antdStrings.ghost)
    //noinspection ScalaUnusedSymbol
    case object Link    extends Type(antdStrings.link)
    //noinspection ScalaUnusedSymbol
    case object Primary extends Type(antdStrings.primary)
    //noinspection ScalaUnusedSymbol
    case object Text    extends Type(antdStrings.text_)
  }

  def apply(buttonType: Type = Type.Default,
            danger: Boolean = false,
            onClick: ReactMouseEventFrom[HTMLElement] => Callback = _ => Callback.empty,
            title: String = "")(
      content: TagMod*) =
    A.Button
      .apply(content*)
      .`type`(buttonType.repr)
      .danger(danger)
      .onClick(onClick)
      .title(title)
}
