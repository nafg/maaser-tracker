package maasertracker.js

import org.scalajs.dom.HTMLElement
import japgolly.scalajs.react.vdom.html_<^.*
import japgolly.scalajs.react.{Callback, ReactMouseEventFrom}
import io.github.nafg.antd.facade.antd.libButtonButtonMod.ButtonType
import io.github.nafg.antd.facade.antd.libCardMod.CardSize
import io.github.nafg.antd.facade.antd.libGridColMod.FlexType
import io.github.nafg.antd.facade.antd.{antdStrings, components as A}

object Facades {
  object Ant {
    def Button(buttonType: ButtonType = antdStrings.default, title: String = "")(
        content: TagMod*)(onClick: ReactMouseEventFrom[HTMLElement] => Callback) =
      A.Button
        .apply(content*)
        .`type`(buttonType)
        .title(title)
        .onClick(onClick)

    def Card(size: CardSize)(content: TagMod*) =
      A.Card
        .apply(content*)
        .size(size)

    def Row(content: TagMod*) =
      A.Row
        .apply(content*)

    def Col(flex: FlexType)(content: TagMod*) =
      A.Col
        .apply(content*)
        .flex(flex)
  }
}
