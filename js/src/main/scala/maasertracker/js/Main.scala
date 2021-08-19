package maasertracker.js

import io.circe.Decoder
import io.circe.syntax.EncoderOps
import japgolly.scalajs.react.extra.Ajax
import japgolly.scalajs.react.vdom.html_<^.*
import japgolly.scalajs.react.{AsyncCallback, Callback, ScalaComponent}
import maasertracker.*
import org.scalajs.dom
import typings.antd.anon.ScrollToFirstRowOnChange
import typings.antd.antdStrings.small
import typings.antd.components.{Transfer as _, _}
import typings.antd.formFormMod.FormLayout
import typings.antd.tooltipMod.TooltipPropsWithTitle
import typings.antd.{antdBooleans, antdStrings}
import typings.rcTable
import typings.react.mod.{CSSProperties, RefAttributes}

import scala.collection.immutable.SortedSet
import scala.concurrent.Future
import scala.math.Ordering.Implicits.seqOrdering
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.JSConverters.JSRichIterableOnce
import scala.scalajs.js.annotation.JSImport

object Main {
  @js.native
  @JSImport("antd/dist/antd.less", JSImport.Default)
  val CSS: js.Object = js.native
  locally(CSS)

  case class State(info: TransactionsInfo = TransactionsInfo(Map.empty, Seq.empty, 0, Nil, Nil, Map.empty),
                   items: Seq[PlaidItem] = Nil,
                   categories: Seq[List[String]] = Nil,
                   loading: Boolean = true,
                   error: Option[String] = None,
                   showExtra: Boolean = false)

  def accountNameParts(acct: AccountInfo) = {
    val i = acct.institution.name
    val a = acct.account.name.replaceAll(i, "").trim
    val t = acct.account.subtype
    (i, a, t)
  }

  def accountLabel(acct: AccountInfo) = {
    val (i, a, t) = accountNameParts(acct)
    val a1        = a match {
      case "" => ""
      case a0 => ": " + a0
    }
    s"$i$a1 ($t)"
  }

  def ajax[A: Decoder](endpoint: String) =
    Ajax
      .get(endpoint)
      .send
      .asAsyncCallback
      .map(xhr => io.circe.parser.decode[A](xhr.responseText))
      .flatMap(result => AsyncCallback.fromFuture(Future.fromTry(result.toTry)))

  def makePlaid(token: String)(onSuccess: (String, js.Dynamic) => Callback) = {
    def tokenParam = token

    def onSuccessParam = onSuccess

    Plaid.create(
      new PlaidCreateParam {
        override val env       = "development"
        override val product   = js.Array("transactions")
        override val token     = tokenParam
        override val onSuccess =
          js.Any.fromFunction2((publicToken, metadata) => onSuccessParam(publicToken, metadata).runNow())
      }
    )
  }

  val component =
    ScalaComponent
      .builder[Unit]
      .initialState(State())
      .render { self =>
        val state = self.state

        Layout.style(CSSProperties().setPadding("24px 24px"))(
          Layout.Content(
            <.div(
              <.div("Loading...").when(state.loading),
              state.error.map(e => <.div("ERROR: " + e)),
              Form().layout(FormLayout.vertical)(
                Row.gutter(16).style(CSSProperties().setMarginBottom(16))(
                  state.items.toTagMod { item =>
                    val maybeErrors = state.info.errors.get(item.itemId)
                    val button      =
                      Button("Update" + (if (maybeErrors.exists(_.nonEmpty)) " (!)" else ""))
                        .onClick { _ =>
                          ajax[String]("/api/linkToken?accessToken=" + item.accessToken)
                            .flatMapSync(token => Callback(makePlaid(token)((_, _) => Callback.empty).open()))
                            .toCallback
                        }
                    Col(
                      Text(item.institution.name + " "),
                      maybeErrors match {
                        case None         => button
                        case Some(errors) =>
                          button
                            .title(errors.map(_.error_message).mkString("\n"))
                      }
                    )
                  },
                  Button
                    .`type`(antdStrings.primary)("Link new item")
                    .onClick { _ =>
                      ajax[String]("/api/plaid-link-token").flatMapSync { plaidLinkToken =>
                        Callback {
                          makePlaid(plaidLinkToken) { (publicToken, metadata) =>
                            Ajax
                              .post("/api/items")
                              .send(
                                AddItemRequest(
                                  publicToken,
                                  io.circe.scalajs.decodeJs[Institution](metadata.institution).toTry.get
                                )
                                  .asJson
                                  .spaces4
                              )
                              .asCallback
                          }
                            .open()
                        }
                      }.toCallback
                    }
                )
              ),
              Table[TransactionsInfo.Item]()
                .pagination(antdBooleans.`false`)
                .dataSource(state.info.transactions.reverse.toJSArray)
                .columnsVarargs(
                  columnType("date", "Date") { tx =>
                    Tooltip(TooltipPropsWithTitle().setTitle(tx.transactionId).setChildren(
                      tx.date.toString
                    ).combineWith(RefAttributes()))
                  },
                  columnType("account", "Account")(t => accountLabel(state.info.accounts(t.accountId)))
                    .filtering(_.accountId)()(
                      state.info.accounts.groupBy(_._2.institution).map { case (institution, accounts) =>
                        FilterItem(
                          institution.institution_id,
                          institution.name,
                          accounts.values
                            .toSeq
                            .sortBy(_.account.name)
                            .map(info => FilterItem(info.id, s"${info.account.name} (${info.account.subtype})"))
                        )
                      }
                    ),
                  columnType("name", "Name")(_.name),
                  columnType("category", "Category")(_.category.mkString(" > "))
                    .filtering(_.category)(_.asJson.noSpaces)(
                      state.categories.toJSArray.map { category =>
                        FilterItem(category, if (category.isEmpty) "None" else category.mkString(" > "))
                      }
                    ),
                  columnType("transactionType", "Type")(_.transactionType),
                  columnType_("amount", "Amount") { t =>
                    val amount = -1 * t.fold(_.withdrawal.amount, _.amount)
                    f"$$$amount%,.2f"
                  },
                  columnTypeTx("tag", "Tag")(t => state.info.tags.get(t.transactionId).mkString)
                    .filtering(t => state.info.tags.get(t.transactionId))(_.fold("")(_.toString))(
                      Tags.values.toList.map(tag => FilterItem(Some(tag), tag.toString))
                    ),
                  columnTypeTx("maaserBalance", "Maaser balance") { t =>
                    f"$$${state.info.maaserBalances(t.transactionId)}%,.2f"
                  }
                )
                .size(small)
                .scroll(
                  js.Dynamic.literal(y = "calc(100vh - 240px)")
                    .asInstanceOf[js.UndefOr[rcTable.anon.X] & ScrollToFirstRowOnChange]
                )
                .rowClassNameFunction3 {
                  case (Right(tx), _, _) =>
                    state.info.tags.get(tx.transactionId) match {
                      case None      => ""
                      case Some(tag) => "tag-" + tag.toString
                    }
                  case _                 => ""
                }
                .rowKeyFunction2 {
                  case (Right(tx), _)                => tx.transactionId
                  case (Left(Transfer(tx1, tx2)), _) => tx1.transactionId + "->" + tx2.transactionId
                }
            )
          )
        )
      }
      .componentDidMount { self =>
        ajax[TransactionsInfo]("/api/transactions")
          .zip(ajax[Seq[PlaidItem]]("/api/items"))
          .flatMapSync { case (info, items) =>
            self.modState { state =>
              val categories =
                info
                  .transactions
                  .flatMap {
                    case Right(tx)                => Seq(tx.category)
                    case Left(Transfer(tx1, tx2)) => Seq(tx1.category, tx2.category)
                  }
                  .to(SortedSet)
              state.copy(
                info = info,
                items = items,
                categories = categories.toSeq,
                loading = false
              )
            }
          }
          .handleErrorSync(t => self.modState(_.copy(loading = false, error = Some(t.toString))))
          .toCallback
      }
      .build

  def main(args: Array[String]): Unit = component().renderIntoDOM(dom.document.getElementById("container"))
}
