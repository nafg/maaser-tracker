package maasertracker.js

import org.scalajs.dom.HTMLElement
import japgolly.scalajs.react.vdom.html_<^.*
import japgolly.scalajs.react.{Callback, ReactMouseEventFrom}
import io.github.nafg.antd.facade.antd.antdStrings
import io.github.nafg.antd.facade.antd.components.{Button, Card}
import io.github.nafg.antd.facade.antd.libButtonButtonMod.ButtonType
import io.github.nafg.antd.facade.antd.libCardMod.CardSize

object Facades {
  def AntButton(buttonType: ButtonType = antdStrings.default, title: String = "")(
      content: TagMod*)(onClick: ReactMouseEventFrom[HTMLElement] => Callback) =
    Button
      .apply(content*)
      .`type`(buttonType)
      .title(title)
      .onClick(onClick)

  def AntCard(size: CardSize)(content: TagMod*) =
    Card
      .apply(content*)
      .size(size)

}
