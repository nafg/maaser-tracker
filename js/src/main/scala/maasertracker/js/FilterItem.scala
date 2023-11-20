package maasertracker.js

import scala.scalajs.js.JSConverters.JSRichIterableOnce

import japgolly.scalajs.react.vdom.html_<^.*
import io.github.nafg.antd.facade.antd.libTableInterfaceMod.ColumnFilterItem

case class FilterItem[A](test: A => Boolean,
                         text: String,
                         children: Seq[FilterItem[A]] = Nil,
                         hideTransfers: Boolean = false) {
  def toAnt(filterItems: FilterItems[A]): ColumnFilterItem = {
    val value = filterItems.toKey(this)
    val item  = ColumnFilterItem(value).setText(text)
    if (children.isEmpty)
      item
    else
      item.setChildren(children.map(_.toAnt(filterItems)).toJSArray)
  }
}

case class FilterItems[A](items: Iterable[FilterItem[A]]) {
  private def mkKeys(items: Iterable[FilterItem[A]]): Map[FilterItem[A], String] =
    items.zipWithIndex
      .flatMap { case (item, n) =>
        Map(item -> n.toString) ++
          mkKeys(item.children).view.mapValues(s"$n-" + _)
      }
      .toMap

  val toKey   = mkKeys(items)
  val fromKey = toKey.map(_.swap)
}
