package maasertracker.js

import scala.scalajs.js.|

import org.scalajs.dom.HTMLElement
import japgolly.scalajs.react.vdom.html_<^.*
import japgolly.scalajs.react.{Callback, ReactMouseEventFrom}
import io.github.nafg.antd.facade.antd.libButtonButtonMod.ButtonType
import io.github.nafg.antd.facade.antd.libCardMod.CardSize
import io.github.nafg.antd.facade.antd.libGridColMod.FlexType
import io.github.nafg.antd.facade.antd.{antdStrings, components as A}
import io.github.nafg.antd.facade.react.mod.CSSProperties

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

    def Col(flex: FlexType)(content: TagMod*) =
      A.Col
        .apply(content*)
        .flex(flex)

    object Layout {
      def apply(style: CSSProperties)(content: TagMod*) =
        A.Layout
          .apply(content*)
          .style(style)

      def Content(content: TagMod*) =
        A.Layout.Content
          .apply(content*)
    }

    def Row(content: TagMod*) =
      A.Row
        .apply(content*)

    def Space(direction: antdStrings.horizontal | antdStrings.vertical = antdStrings.horizontal)(content: TagMod*) =
      A.Space
        .apply(content*)
        .direction(direction)
  }
}
