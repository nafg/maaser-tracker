package maasertracker.js

import java.time.LocalTime
import java.time.format.{DateTimeFormatter, FormatStyle}

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

import org.scalajs.dom
import japgolly.scalajs.react.vdom.html_<^.*
import japgolly.scalajs.react.{AsyncCallback, ScalaComponent}

import maasertracker.TransactionsInfo

trait Refresher {
  def reloadTransactions: AsyncCallback[Unit]
  def reloadMatchers: AsyncCallback[Unit]
}

object Main {
  @js.native
  @JSImport("antd/dist/antd.less", JSImport.Default)
  private val CSS: js.Object = js.native
  locally(CSS)

  private sealed trait State
  private object State {
    case object Pending                                          extends State
    case class Failure(error: String, retryTime: FiniteDuration) extends State
    case class Success(transactionsInfo: TransactionsInfo)       extends State
  }

  private def update(setState: State => AsyncCallback[Unit], retry: FiniteDuration)(
      load: AsyncCallback[TransactionsInfo]): AsyncCallback[Unit] =
    load
      .flatMap(info => setState(State.Success(info)))
      .handleError { t =>
        t.printStackTrace()
        setState(State.Failure(t.toString, retry)) >>
          loadAll(setState, duration = (retry * 2).min(2.minute))
            .delay(retry)
      }

  private def loadAll(setState: State => AsyncCallback[Unit], duration: FiniteDuration) =
    update(setState, duration)(Api.Transactions.get
      .zip(Api.MatchRules.get)
      .map { case (transactions, matchers) => TransactionsInfo(transactions, matchers) })

  private val component                                                                 =
    ScalaComponent.builder[Unit]
      .initialState[State](State.Pending)
      .render { self =>
        self.state match {
          case State.Pending                   => <.div("Loading...")
          case State.Failure(error, retryTime) =>
            <.div(
              "ERROR: ",
              error,
              <.br,
              "Retry at ",
              LocalTime.now().plusSeconds(retryTime.toSeconds)
                .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM))
            )
          case State.Success(transactionsInfo) =>
            Router.router(
              Router.Props(
                info = transactionsInfo,
                refresh = new Refresher {
                  override def reloadTransactions =
                    update(self.setStateAsync, 1.seconds)(
                      Api.Transactions.get
                        .map(transactions => transactionsInfo.copy(transactions = transactions))
                    )
                  override def reloadMatchers     =
                    update(self.setStateAsync, 1.seconds)(
                      Api.MatchRules.get
                        .map(matchers => transactionsInfo.copy(matchers = matchers))
                    )
                }
              )
            )
        }
      }
      .componentDidMount(self => loadAll(self.setStateAsync, 1.seconds))
      .build

  def main(args: Array[String]): Unit = component().renderIntoDOM(dom.document.getElementById("container"))
}
