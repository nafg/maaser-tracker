package maasertracker.js

import scala.scalajs.js.JSConverters.JSRichIterableOnce
import scala.scalajs.js.|

import japgolly.scalajs.react.vdom.html_<^.*

import maasertracker.{Transaction, TransactionsInfo}
import typings.antd.antdStrings.tree
import typings.antd.tableInterfaceMod.{ColumnFilterItem, ColumnType}

case class FilterItem[A](test: A => Boolean,
                         text: String,
                         children: Seq[FilterItem[A]] = Nil,
                         hideTransfers: Boolean = false)

class FilterType[A](get: Transaction => A)(items: Iterable[FilterItem[A]]) {
  private type FilterValueType = String | Double | Boolean
  private def mkKeys(items: Iterable[FilterItem[A]]): Map[FilterItem[A], FilterValueType] =
    items.zipWithIndex
      .flatMap { case (item, n) =>
        Map(item -> n.toString) ++
          mkKeys(item.children).view.mapValues(s"$n/" + _)
      }
      .toMap

  val toKey   = mkKeys(items)
  val fromKey = toKey.map(_.swap)

  private def toAnt(filterItem: FilterItem[A]): ColumnFilterItem = {
    val item = ColumnFilterItem(toKey(filterItem)).setText(filterItem.text)
    if (filterItem.children.isEmpty)
      item
    else
      item.setChildren(filterItem.children.map(toAnt).toJSArray)
  }

  def filtering(current: Set[FilterItem[A]], columnType: ColumnType[TransactionsInfo.Item]) =
    columnType
      .setFilters(items.map(toAnt).toJSArray)
      .setFilterMode(tree)
      .setFilteredValue(current.toJSArray.map(toKey))
      .setOnFilter { (value: FilterValueType, item) =>
        val filter = fromKey(value)
        item.fold(
          t => !filter.hideTransfers && (filter.test(get(t.withdrawal)) || filter.test(get(t.deposit))),
          tx => filter.test(get(tx))
        )
      }
}
