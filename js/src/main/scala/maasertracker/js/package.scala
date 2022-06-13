package maasertracker

import io.circe.Decoder
import japgolly.scalajs.react.extra.Ajax
import japgolly.scalajs.react.vdom.html_<^.*
import japgolly.scalajs.react.{AsyncCallback, React, facade}
import typings.antd.antdStrings.tree
import typings.antd.tableInterfaceMod.ColumnType
import typings.rcTable.interfaceMod.RenderedCell

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.JSConverters.JSRichIterableOnce
import scala.scalajs.js.|

package object js {
  def columnType_(key: String, title: String)(render: TransactionsInfo.Item => VdomNode) =
    ColumnType[TransactionsInfo.Item]()
      .setKey(key)
      .setTitle(title)
      .setRender((_, t, _) => render(t).rawNode.asInstanceOf[facade.React.Node | RenderedCell[TransactionsInfo.Item]])

  def columnType(key: String, title: String)(render: Transaction => VdomNode) =
    columnType_(key, title) {
      case Right(tx)                => render(tx)
      case Left(Transfer(tx1, tx2)) =>
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

  def columnTypeTx(key: String, title: String)(render: Transaction => VdomNode) =
    columnType_(key, title) {
      case Right(tx) => render(tx)
      case Left(_)   => EmptyVdom
    }

  implicit class ColumnType_extensions(self: ColumnType[TransactionsInfo.Item]) {
    private def mkKeys[A](items: Iterable[FilterItem[A]]): Map[FilterItem[A], String] =
      items.zipWithIndex
        .flatMap { case (item, n) =>
          Map(item -> n.toString) ++
            mkKeys(item.children).view.mapValues(s"$n/" + _)
        }
        .toMap
    def filtering[A](get: Transaction => A)(items: Iterable[FilterItem[A]]) = {
      val toKey   = mkKeys(items)
      val fromKey = toKey.map(_.swap)
      self
        .setFilters(items.map(_.toAnt(toKey)).toJSArray)
        .setFilterMode(tree)
        .setOnFilter { (value, item) =>
          val filter = fromKey(value.asInstanceOf[String])
          item.fold(
            t => !filter.hideTransfers && (filter.test(get(t.withdrawal)) || filter.test(get(t.deposit))),
            tx => filter.test(get(tx))
          )
        }
    }
  }

  def ajax[A: Decoder](endpoint: String) =
    Ajax
      .get(endpoint)
      .send
      .asAsyncCallback
      .map(xhr => io.circe.parser.decode[A](xhr.responseText))
      .flatMap(result => AsyncCallback.fromFuture(Future.fromTry(result.toTry)))
}
