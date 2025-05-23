package maasertracker.js.facades.ant

import scala.scalajs.js.|

import japgolly.scalajs.react.Callback
import japgolly.scalajs.react.vdom.html_<^.*
import io.github.nafg.antd.facade.antd.libDrawerMod.placementType
import io.github.nafg.antd.facade.antd.{antdStrings, components as A}

import maasertracker.js.any_foldNull

object Drawer {
  sealed abstract class Placement(val repr: placementType)
  object Placement {
    case object Left   extends Placement(antdStrings.left)
    case object Right  extends Placement(antdStrings.right)
    //noinspection ScalaUnusedSymbol
    case object Top    extends Placement(antdStrings.top)
    //noinspection ScalaUnusedSymbol
    case object Bottom extends Placement(antdStrings.bottom)
  }

  def apply(onClose: Callback,
            placement: Placement = null,
            title: VdomNode = null,
            visible: Boolean,
            width: Double | String = null)(children: TagMod*): VdomElement =
    A.Drawer
      .visible(visible)
      .onClose(_ => onClose)
      .foldNull(_.title)(title.rawNode)
      .foldNull(_.width)(width)
      .foldNull(_.placement)(placement.repr)
      .apply(children*)
}
