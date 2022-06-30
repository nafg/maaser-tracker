package maasertracker.js

import scala.scalajs.js.JSConverters.JSRichIterableOnce
import scala.scalajs.js.|

import japgolly.scalajs.react.vdom.html_<^.*

import typings.antd.tableInterfaceMod.ColumnFilterItem

case class FilterItem[A](test: A => Boolean,
                         text: String,
                         children: Seq[FilterItem[A]] = Nil,
                         hideTransfers: Boolean = false) {
  def toAnt(filterItems: FilterItems[A]): ColumnFilterItem = {
    val item = ColumnFilterItem(filterItems.toKey(this)).setText(text)
    if (children.isEmpty)
      item
    else
      item.setChildren(children.map(_.toAnt(filterItems)).toJSArray)
  }
}

case class FilterItems[A](items: Iterable[FilterItem[A]]) {
  type FilterValueType = String | Double | Boolean

  private def mkKeys(items: Iterable[FilterItem[A]]): Map[FilterItem[A], FilterValueType] =
    items.zipWithIndex
      .flatMap { case (item, n) =>
        Map(item -> n.toString) ++
          mkKeys(item.children).view.mapValues(s"$n-" + _)
      }
      .toMap

  val toKey   = mkKeys(items)
  val fromKey = toKey.map(_.swap)
}
