package maasertracker.js.facades.ant

import scala.scalajs.js.|

import japgolly.scalajs.react.Callback
import japgolly.scalajs.react.vdom.html_<^.*
import io.github.nafg.antd.facade.antd.libDrawerMod.placementType
import io.github.nafg.antd.facade.antd.{antdStrings, components as A}

object Drawer {
  sealed abstract class Placement(val repr: placementType)
  object Placement {
    case object Left   extends Placement(antdStrings.left)
    case object Right  extends Placement(antdStrings.right)
    case object Top    extends Placement(antdStrings.top)
    case object Bottom extends Placement(antdStrings.bottom)
  }

  def apply(onClose: Callback,
            placement: Placement = null,
            title: VdomNode = null,
            visible: Boolean,
            width: Double | String = null)(children: TagMod*): VdomElement = {
    val drawer        =
      A.Drawer
        .visible(visible)
        .onClose(_ => onClose)
    val withTitle     = if (title != null) drawer.title(title.rawNode) else drawer
    val withWidth     = if (width != null) withTitle.width(width) else withTitle
    val withPlacement = if (placement != null) withWidth.placement(placement.repr) else withWidth
    withPlacement(children*)
  }
}
