package maasertracker.js.facades.ant

import scala.scalajs.js.|

import japgolly.scalajs.react.vdom.html_<^.*
import io.github.nafg.antd.facade.antd.anon.PartialRecordBreakpointnu
import io.github.nafg.antd.facade.antd.{antdStrings, components as A}

import maasertracker.js.any_foldNull

object Descriptions {
  sealed abstract class Size(val repr: antdStrings.middle | antdStrings.small | antdStrings.default)
  object Size {
    case object Default extends Size(antdStrings.default)
    //noinspection ScalaUnusedSymbol
    case object Middle  extends Size(antdStrings.middle)
    case object Small   extends Size(antdStrings.small)
  }

  sealed abstract class Layout(val repr: antdStrings.horizontal | antdStrings.vertical)
  object Layout {
    case object Horizontal extends Layout(antdStrings.horizontal)
    case object Vertical   extends Layout(antdStrings.vertical)
  }

  case class Item(label: VdomNode, span: Int = 1)(children: TagMod*) {
    def toAnt: VdomElement =
      A.Descriptions.Item
        .label(label.rawNode)
        .span(span)
        .apply(children*)
  }

  def apply(
      bordered: Boolean = false,
      colon: Boolean = true,
      column: Double | PartialRecordBreakpointnu = null,
      layout: Layout = null,
      size: Size = null,
      title: VdomNode = null
  )(items: Item*): VdomElement =
    A.Descriptions
      .bordered(bordered)
      .colon(colon)
      .foldNull(_.column)(column)
      .foldNull(_.layout)(if (layout == null) null else layout.repr)
      .foldNull(_.size)(if (size == null) null else size.repr)
      .foldNull(_.title)(title.rawNode)
      .apply(items.map(_.toAnt)*)
}
