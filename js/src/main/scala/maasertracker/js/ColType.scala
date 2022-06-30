package maasertracker.js

import scala.scalajs.js.JSConverters.JSRichIterableOnce
import scala.scalajs.js.|

import japgolly.scalajs.react.vdom.html_<^.*
import japgolly.scalajs.react.{React, facade}

import maasertracker.{Transaction, TransactionsInfo, Transfer}
import monocle.Lens
import typings.antd.antdStrings.tree
import typings.antd.tableInterfaceMod.{ColumnType, FilterValue}
import typings.rcTable.interfaceMod.RenderedCell
import typings.std.Record

trait BaseColType {
  def key: String

  def toAnt(state: TransactionsView.State): ColumnType[TransactionsInfo.Item]
}

case class ColType(override val key: String, title: String, render: TransactionsInfo.Item => VdomNode = _ => EmptyVdom)
    extends BaseColType {

  def withRender(single: Transaction => VdomNode, transfer: Transfer => VdomNode = _ => EmptyVdom) =
    copy(render = _.fold(transfer, single))

  def withRenderEach(render: Transaction => VdomNode) =
    withRender(
      render,
      {
        case Transfer(tx1, tx2) =>
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

  def filtering[A](get: Transaction => A, lens: Lens[TransactionsView.State, Set[FilterItem[A]]])(
      items: Iterable[FilterItem[A]]) = FilteringColType(this, FilterItems(items), get, lens)

  override def toAnt(state: TransactionsView.State) =
    ColumnType[TransactionsInfo.Item]()
      .setKey(key)
      .setTitle(title)
      .setRender((_, t, _) => render(t).rawNode.asInstanceOf[facade.React.Node | RenderedCell[TransactionsInfo.Item]])
}

case class FilteringColType[A](colType: ColType,
                               filterItems: FilterItems[A],
                               get: Transaction => A,
                               lens: Lens[TransactionsView.State, Set[FilterItem[A]]])
    extends BaseColType {
  override def key = colType.key

  override def toAnt(state: TransactionsView.State) =
    colType.toAnt(state)
      .setFilterMode(tree)
      .setFilteredValue(lens.get(state).toJSArray.map(filterItems.toKey))
      .setFilters(filterItems.items.map(_.toAnt(filterItems)).toJSArray)
      .setOnFilter { (value: filterItems.FilterValueType, item) =>
        val filter = filterItems.fromKey(value)
        item.fold(
          t =>
            !filter.hideTransfers &&
              (filter.test(get(t.withdrawal)) || filter.test(get(t.deposit))),
          tx => filter.test(get(tx))
        )
      }

  def handleOnChange(filters: Record[String, FilterValue | Null], state: TransactionsView.State) =
    lens.replace(
      filters.get(key)
        .flatMap(nullableToOption)
        .map(_.map(filterItems.fromKey(_)).toSet)
        .getOrElse(Set.empty)
    )(state)

  def stateToKeys(state: TransactionsView.State) =
    lens.get(state).map(filterItems.toKey)

  def keysToState(keys: Iterable[String]) =
    lens.replace(keys.flatMap(filterItems.fromKey.get(_)).toSet)
}
