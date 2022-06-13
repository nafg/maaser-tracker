package maasertracker.js

import japgolly.scalajs.react.vdom.html_<^.*
import typings.antd.tableInterfaceMod.ColumnFilterItem

import scala.scalajs.js.JSConverters.JSRichIterableOnce

case class FilterItem[A](test: A => Boolean,
                         text: String,
                         children: Seq[FilterItem[A]] = Nil,
                         hideTransfers: Boolean = false) {
  def toAnt(repr: FilterItem[A] => String): ColumnFilterItem = {
    val item = ColumnFilterItem(repr(this)).setText(text)
    if (children.isEmpty)
      item
    else
      item.setChildren(children.map(_.toAnt(repr)).toJSArray)
  }
}
