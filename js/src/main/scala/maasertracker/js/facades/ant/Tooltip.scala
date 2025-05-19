package maasertracker.js.facades.ant

import japgolly.scalajs.react.vdom.html_<^.*
import io.github.nafg.antd.facade.antd.components as A
import io.github.nafg.antd.facade.antd.libTooltipMod.TooltipProps
import io.github.nafg.antd.facade.react.mod.RefAttributes

object Tooltip {
  def apply(title: VdomNode)(children: VdomNode) =
    A.Tooltip
      .apply(
        TooltipProps.TooltipPropsWithTitle()
          .setTitle(title.rawNode)
          .setChildren(children)
          .combineWith(RefAttributes[Any]())
      )

}
