package maasertracker.js.facades.ant

import japgolly.scalajs.react.vdom.html_<^.*
import io.github.nafg.antd.facade.antd.components as A

object Row {
  def apply(content: TagMod*) =
    A.Row
      .apply(content*)
}
