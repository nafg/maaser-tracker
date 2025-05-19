package maasertracker.js.facades.ant

import scala.scalajs.js

import japgolly.scalajs.react.vdom.html_<^.*
import io.github.nafg.antd.facade.antd.components as A
import io.github.nafg.antd.facade.react.mod.CSSProperties

object Layout {
  def apply(style: js.UndefOr[CSSProperties] = js.undefined)(content: TagMod*) = {
    val step1 = A.Layout()
    style.fold(step1)(step1.style)
      .apply(content*)
  }

  def Content(content: TagMod*) =
    A.Layout.Content
      .apply(content*)
}
