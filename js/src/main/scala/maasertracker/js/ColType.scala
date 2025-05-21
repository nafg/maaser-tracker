package maasertracker.js

import scala.scalajs.js.JSConverters.JSRichIterableOnce
import scala.scalajs.js.|

import japgolly.scalajs.react.React
import japgolly.scalajs.react.extra.StateSnapshot
import japgolly.scalajs.react.facade.React.Node
import japgolly.scalajs.react.vdom.html_<^.*
import io.github.nafg.antd.facade.antd.antdStrings.tree
import io.github.nafg.antd.facade.antd.libTableInterfaceMod.ColumnType
import io.github.nafg.antd.facade.rcTable.libInterfaceMod.RenderedCell

import maasertracker.{Transaction, PlaidData, TransactionsInfo, Transfer}

sealed trait ColType
object ColType {
  def apply(key: String, title: String) = Simple(key, title)

  case class Simple(
      key: String,
      title: String,
      render: PlaidData.Item => StateSnapshot[PageParams] => VdomNode = _ => _ => EmptyVdom)
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

    def filtering[A](get: Transaction => A)(filterSpec: FilterSpec[PageParams, A]) = Filtering(this, filterSpec, get)
  }

  case class Filtering[A](colType: Simple, filterSpec: FilterSpec[PageParams, A], get: Transaction => A)
      extends ColType

  private def toAnt(pageParams: StateSnapshot[PageParams], simple: Simple) =
    ColumnType[PlaidData.Item]()
      .setKey(simple.key)
      .setTitle(simple.title)
      .setRender((_, t, _) =>
        simple.render(t)(pageParams).rawNode.asInstanceOf[Node | RenderedCell[PlaidData.Item]]
      )

  def toAnt(info: TransactionsInfo,
            pageParams: StateSnapshot[PageParams],
            colType: ColType): ColumnType[PlaidData.Item] =
    colType match {
      case simple: Simple                              => toAnt(pageParams, simple)
      case ColType.Filtering(colType, filterSpec, get) =>
        val filterItems = filterSpec.filterItemsFunc(info)
        toAnt(pageParams, colType)
          .setFilterMode(tree)
          .setFilteredValue(filterSpec.lens.get(pageParams.value).toJSArray.map(filterItems.toKey))
          .setFilters(filterItems.toAnt)
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
