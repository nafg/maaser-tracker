package maasertracker.js

import io.circe.Decoder
import io.circe.syntax.EncoderOps
import japgolly.scalajs.react.extra.Ajax
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{AsyncCallback, Callback, React, ScalaComponent}
import maasertracker._
import org.scalajs.dom
import typings.antd.anon.ScrollToFirstRowOnChange
import typings.antd.antdStrings.small
import typings.antd.components.{Transfer => _, _}
import typings.antd.formFormMod.FormLayout
import typings.antd.tableInterfaceMod.ColumnType
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

  case class State(info: TransactionsInfo = TransactionsInfo(Map.empty, Seq.empty, 0, Nil, Nil),
                   items: Seq[PlaidItem] = Nil,
                   categories: Seq[List[String]] = Nil,
                   visibleAccounts: Set[String] = Set.empty,
                   visibleCategories: SortedSet[List[String]] = SortedSet.empty,
                   loading: Boolean = true,
                   error: Option[String] = None,
                   showExtra: Boolean = false) {
    lazy val visibleTransactions = {
      def isVisible(tx: Transaction) = visibleAccounts.contains(tx.accountId) && visibleCategories.contains(tx.category)

      info.transactions.filter {
        case Right(tx)                => isVisible(tx)
        case Left(Transfer(tx1, tx2)) => isVisible(tx1) || isVisible(tx2)
      }
    }
  }

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

        def columnType_(key: String, title: String)(render: Either[Transfer, Transaction] => VdomNode) =
          ColumnType[Either[Transfer, Transaction]]()
            .setKey(key)
            .setTitle(title)
            .setRender((_, t, _) => render(t).rawNode)

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

        Layout.style(CSSProperties().setPadding("24px 24px"))(
          Layout.Content(
            <.div(
              <.div("Loading...").when(state.loading),
              state.error.map(e => <.div("ERROR: " + e)),
              Form().layout(FormLayout.vertical)(
                Row.gutter(16)(
                  state.items.toTagMod { item =>
                    Col(
                      Text(item.institution.name + " "),
                      Button("Update")
                        .onClick { _ =>
                          ajax[String]("/api/linkToken?accessToken=" + item.accessToken)
                            .flatMapSync(token => Callback(makePlaid(token)((_, _) => Callback.empty).open()))
                            .toCallback
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
                ),
                Row.gutter(16)(
                  Col.span(12)(
                    Form.Form().label("Accounts")(
                      Select[js.Array[String]]()
                        .mode(antdStrings.multiple)
                        .value(state.visibleAccounts.toJSArray)
                        .onChange((value, _) => self.modState(_.copy(visibleAccounts = value.toSet)))(
                          state.info.accounts.groupBy(_._2.institution).toVdomArray { case (institution, accounts) =>
                            Select
                              .OptGroup.withKey(institution.institution_id).label(institution.name)(
                                accounts.toVdomArray { case (accountId, acct) =>
                                  Select.Option(accountId)(s"${acct.account.name} (${acct.account.subtype})")
                                }
                              )
                          }
                        )
                    )
                  ),
                  Col.span(12)(
                    Form.Form().label("Categories")(
                      Select[js.Array[String]]()
                        .mode(antdStrings.multiple)
                        .maxTagCount(5)
                        .value(state.visibleCategories.map(_.asJson.noSpaces).toJSArray)
                        .onChange { (value, _) =>
                          self.modState { state =>
                            state.copy(
                              visibleCategories =
                                value.map(str => io.circe.parser.decode[List[String]](str).toTry.get).to(SortedSet)
                            )
                          }
                        }(
                          state.categories.distinct.toVdomArray { cat =>
                            Select.Option(cat.asJson.noSpaces)(
                              if (cat.isEmpty) "None"
                              else cat.mkString(" > ")
                            )
                          }
                        )
                    )
                  )
                )
              ),
              Table[Either[Transfer, Transaction]]()
                .pagination(antdBooleans.`false`)
                .dataSource(state.visibleTransactions.reverse.toJSArray)
                .columnsVarargs(
                  columnType("date", "Date") { tx =>
                    Tooltip(TooltipPropsWithTitle().setTitle(tx.transactionId).setChildren(
                      tx.date.toString
                    ).combineWith(RefAttributes()))
                  },
                  columnType("account", "Account")(t => accountLabel(state.info.accounts(t.accountId))),
                  columnType("name", "Name")(_.name),
                  columnType("category", "Category")(_.category.mkString(" > ")),
                  columnType("transactionType", "Type")(_.transactionType),
                  columnType_("amount", "Amount") { t =>
                    val amount = -1 * t.fold(_.withdrawal.amount, _.amount)
                    f"$$$amount%,.2f"
                  },
                  columnTypeTx("tag", "Tag")(t => state.info.tags.get(t.transactionId).mkString),
                  columnTypeTx("maaserBalance", "Maaser balance") { t =>
                    f"$$${state.info.maaserBalances(t.transactionId)}%,.2f"
                  }
                )
                .size(small)
                .scroll(
                  js.Dynamic.literal(y = "calc(100vh - 240px)")
                    .asInstanceOf[js.UndefOr[rcTable.anon.X] with ScrollToFirstRowOnChange]
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
                visibleAccounts = info.accounts.keySet,
                visibleCategories = categories,
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
