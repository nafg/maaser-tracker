package maasertracker.js

import scala.collection.immutable.SortedSet
import scala.math.Ordering.Implicits.seqOrdering
import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

import org.scalajs.dom
import japgolly.scalajs.react.vdom.html_<^.*
import japgolly.scalajs.react.{Callback, ScalaComponent}

import maasertracker.{PlaidItem, TransactionsInfo, Transfer}

object Main {
  @js.native
  @JSImport("antd/dist/antd.less", JSImport.Default)
  private val CSS: js.Object = js.native
  locally(CSS)

  case class State(info: TransactionsInfo, items: Seq[PlaidItem], categories: Seq[List[String]])

  private def loadData(setState: Either[String, State] => Callback) =
    Ajax.get[TransactionsInfo]("/api/transactions")
      .zip(Ajax.get[Seq[PlaidItem]]("/api/items"))
      .flatMapSync { case (info, items) =>
        val categories =
          info
            .transactions
            .flatMap {
              case Right(tx)                => Seq(tx.category)
              case Left(Transfer(tx1, tx2)) => Seq(tx1.category, tx2.category)
            }
            .to(SortedSet)
        val state      = State(info = info, items = items, categories = categories.toSeq)
        setState(Right(state))
      }
      .handleErrorSync { t =>
        t.printStackTrace()
        setState(Left(t.toString))
      }
      .toCallback

  val component =
    ScalaComponent.builder[Unit]
      .initialState(Option.empty[Either[String, State]])
      .render { self =>
        self.state match {
          case None               => <.div("Loading...")
          case Some(Left(error))  => <.div("ERROR: " + error)
          case Some(Right(state)) =>
            TransactionsView.router(
              TransactionsView.Props(
                state = state,
                refresh = loadData(state => self.setState(Some(state)))
              )
            )
        }
      }
      .componentDidMount(self => loadData(state => self.setState(Some(state))))
      .build

  def main(args: Array[String]): Unit = component().renderIntoDOM(dom.document.getElementById("container"))
}
