package maasertracker.js.facades.ant

import scala.scalajs.js

import org.scalajs.dom.HTMLElement
import japgolly.scalajs.react.vdom.html_<^.*
import japgolly.scalajs.react.{Callback, ReactMouseEventFrom}
import io.github.nafg.antd.facade.antd.components as A

import maasertracker.js.any_foldNull

object Tag {
  def apply(closable: js.UndefOr[Boolean] = js.undefined,
            onClose: ReactMouseEventFrom[HTMLElement] => Callback = null)(content: TagMod*): VdomElement =
    A.Tag()
      .foldUndefined(_.closable)(closable)
      .foldNull(_.onClose)(onClose)
      .apply(content*)

}
