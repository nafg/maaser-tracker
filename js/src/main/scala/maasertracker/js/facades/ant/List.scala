package maasertracker.js.facades.ant

import scala.scalajs.js.JSConverters.JSRichIterableOnce

import japgolly.scalajs.react.vdom.html_<^.*
import io.github.nafg.antd.facade.antd.libListMod.ListItemLayout
import io.github.nafg.antd.facade.antd.{antdStrings, components as A}

import maasertracker.js.any_foldNull

object List {
  sealed abstract class ItemLayout(val repr: ListItemLayout)
  object ItemLayout {
    case object Horizontal extends ItemLayout(antdStrings.horizontal)
    case object Vertical   extends ItemLayout(antdStrings.vertical)
  }

  case class Item(actions: Seq[VdomNode] = null)(children: TagMod*) {
    def toAnt: VdomElement =
      A.List.Item
        .apply(children*)
        .actions(actions.map(_.rawNode).toJSArray)
  }

  object Item {
    def Meta(avatar: VdomNode = null, description: VdomNode = null, title: VdomNode = null): VdomElement =
      A.List.Item.Meta()
        .foldNull(_.avatar)(if (avatar == null) null else avatar.rawNode)
        .foldNull(_.description)(if (description == null) null else description.rawNode)
        .foldNull(_.title)(if (title == null) null else title.rawNode)
  }

  def apply(header: VdomNode = null, itemLayout: ItemLayout = null)(items: Seq[Item]): VdomElement =
    A.List[Any]()
      .foldNull(_.header)(if (header == null) null else header.rawNode)
      .foldNull(_.itemLayout)(if (itemLayout == null) null else itemLayout.repr)
      .apply(items.map[TagMod](_.toAnt)*)
}
