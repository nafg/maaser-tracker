package maasertracker

import io.circe.Decoder
import japgolly.scalajs.react.extra.Ajax
import japgolly.scalajs.react.vdom.html_<^.*
import japgolly.scalajs.react.{AsyncCallback, React, facade}
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
    def filtering[A](get: Transaction => A)(repr: A => String = (_: A).toString)(items: Iterable[FilterItem[A]]) =
      self
        .setFilters(items.map(_.toAnt(repr)).toJSArray)
        .setOnFilter { (value, item) =>
          val str = value.asInstanceOf[String]
          item.fold(
            t => repr(get(t.withdrawal)) == str || repr(get(t.deposit)) == str,
            tx => repr(get(tx)) == str
          )
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
