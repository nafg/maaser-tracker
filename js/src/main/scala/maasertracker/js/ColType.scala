package maasertracker.js

import scala.scalajs.js.JSConverters.JSRichIterableOnce
import scala.scalajs.js.|

import japgolly.scalajs.react.extra.StateSnapshot
import japgolly.scalajs.react.vdom.html_<^.*
import japgolly.scalajs.react.{React, facade}
import io.github.nafg.antd.facade.antd.antdStrings.tree
import io.github.nafg.antd.facade.antd.libTableInterfaceMod.{ColumnType, FilterValue}
import io.github.nafg.antd.facade.rcTable.libInterfaceMod.RenderedCell
import io.github.nafg.antd.facade.std.Record

import maasertracker.TransactionsInfo.Item
import maasertracker.{Transaction, TransactionsInfo, Transfer}
import monocle.Lens

sealed trait ColType {
  def key: String
}
object ColType       {
  def apply(key: String, title: String) = Simple(key, title)

  case class Simple(
      override val key: String,
      title: String,
      render: TransactionsInfo.Item => StateSnapshot[PageParams] => VdomNode = _ => _ => EmptyVdom)
      extends ColType {

    def withRender(single: Transaction => StateSnapshot[PageParams] => VdomNode,
                   transfer: Transfer => StateSnapshot[PageParams] => VdomNode = _ => _ => EmptyVdom) =
      copy(render = _.fold(transfer, single))

    def withRenderEach(render: Transaction => VdomNode) =
      withRender(
        transaction => _ => render(transaction),
        {
          case Transfer(tx1, tx2) =>
            _ =>
              val s1 = render(tx1)
              val s2 = render(tx2)
              if (s1 == s2)
                s1
              else
                React.Fragment(
                  <.div(s1),
                  " -> ",
                  <.div(s2)
                )
        }
      )

    def filtering[A](get: Transaction => A, lens: Lens[PageParams, Set[FilterItem[A]]])(
        items: Iterable[FilterItem[A]]) = Filtering(this, FilterItems(items), get, lens)
  }

  case class Filtering[A](colType: Simple,
                          filterItems: FilterItems[A],
                          get: Transaction => A,
                          lens: Lens[PageParams, Set[FilterItem[A]]])
      extends ColType {
    override def key = colType.key

    private def decodeFilters(filters: Record[String, FilterValue | Null]): Set[FilterItem[A]] =
      filters.get(key)
        .flatMap(nullableToOption)
        .map(_.map(key => filterItems.fromKey(key.toString)).toSet)
        .getOrElse(Set.empty)

    def applyFilters(filters: Record[String, FilterValue | Null]): PageParams => PageParams =
      lens.replace(decodeFilters(filters))

    def pageParamsToKeys(pageParams: PageParams) = lens.get(pageParams).map(filterItems.toKey)

    def keysToPageParams(keys: Iterable[String]) = lens.replace(keys.flatMap(filterItems.fromKey.get).toSet)
  }

  def toAnt(pageParams: StateSnapshot[PageParams], colType: ColType): ColumnType[Item] =
    colType match {
      case ColType.Simple(key, title, render)                 =>
        ColumnType[TransactionsInfo.Item]()
          .setKey(key)
          .setTitle(title)
          .setRender((_, t, _) =>
            render(t)(pageParams).rawNode.asInstanceOf[facade.React.Node | RenderedCell[TransactionsInfo.Item]]
          )
      case ColType.Filtering(colType, filterItems, get, lens) =>
        toAnt(pageParams, colType)
          .setFilterMode(tree)
          .setFilteredValue(lens.get(pageParams.value).toJSArray.map(filterItems.toKey))
          .setFilters(filterItems.items.map(_.toAnt(filterItems)).toJSArray)
          .setOnFilter { (value, item) =>
            val filter = filterItems.fromKey(value.toString)
            item.fold(
              t =>
                !filter.hideTransfers &&
                  (filter.test(get(t.withdrawal)) || filter.test(get(t.deposit))),
              tx => filter.test(get(tx))
            )
          }
    }
}
