package maasertracker.js

import scala.scalajs.js
import scala.scalajs.js.JSConverters.JSRichIterableOnce

import japgolly.scalajs.react.vdom.html_<^.*
import io.github.nafg.antd.facade.antd.libTableInterfaceMod.ColumnFilterItem

case class FilterItem[A](text: String, children: Seq[FilterItem[A]] = Nil, hideTransfers: Boolean = false)(
    val test: A => Boolean)

case class FilterItems[A](items: Iterable[FilterItem[A]]) {
  private def mkKeys(items: Iterable[FilterItem[A]]): Map[FilterItem[A], String] =
    items.zipWithIndex
      .flatMap { case (item, n) =>
        Map(item -> n.toString) ++ mkKeys(item.children).view.mapValues(s"$n-" + _)
      }
      .toMap

  lazy val toKey: Map[FilterItem[A], String]   = mkKeys(items)
  lazy val fromKey: Map[String, FilterItem[A]] = toKey.map(_.swap)

  private def toAnt(filterItems: Iterable[FilterItem[A]]): js.Array[ColumnFilterItem] =
    filterItems
      .map { filterItem =>
        val value    = this.toKey(filterItem)
        val step1    =
          ColumnFilterItem(value)
            .setText(filterItem.text)
        val children = filterItem.children
        if (children.isEmpty)
          step1
        else
          step1
            .setChildren(toAnt(children))
      }
      .toJSArray

  def toAnt: js.Array[ColumnFilterItem] = toAnt(items)
}
