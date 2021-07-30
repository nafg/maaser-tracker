package maasertracker.js

import japgolly.scalajs.react.vdom.html_<^._
import typings.antd.tableInterfaceMod.ColumnFilterItem

import scala.scalajs.js.JSConverters.JSRichIterableOnce

case class FilterItem[A](value: A, text: String, children: Seq[FilterItem[A]] = Nil) {
  def toAnt(repr: A => String): ColumnFilterItem = {
    val item = ColumnFilterItem(repr(value)).setText(text)
    if (children.isEmpty)
      item
    else
      item.setChildren(children.map(_.toAnt(repr)).toJSArray)
  }
}
