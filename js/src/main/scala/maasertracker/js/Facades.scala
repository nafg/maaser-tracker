package maasertracker.js

import org.scalajs.dom.HTMLElement
import japgolly.scalajs.react.vdom.html_<^.*
import japgolly.scalajs.react.{Callback, ReactMouseEventFrom}
import io.github.nafg.antd.facade.antd.antdStrings
import io.github.nafg.antd.facade.antd.components.Button
import io.github.nafg.antd.facade.antd.libButtonButtonMod.ButtonType

object Facades {
  def AntButton(content: TagMod,
                buttonType: ButtonType = antdStrings.default,
                title: String = "")(onClick: ReactMouseEventFrom[HTMLElement] => Callback) =
    Button
      .apply(content)
      .`type`(buttonType)
      .title(title)
      .onClick(onClick)

}
