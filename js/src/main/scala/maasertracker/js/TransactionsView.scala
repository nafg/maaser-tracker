package maasertracker.js

import io.circe.syntax.EncoderOps
import japgolly.scalajs.react.extra.Ajax
import japgolly.scalajs.react.vdom.html_<^.*
import japgolly.scalajs.react.{Callback, CallbackTo, ScalaComponent}
import kantan.csv.ops.*
import kantan.csv.{HeaderEncoder, RowEncoder, rfc}
import maasertracker.*
import org.scalajs.dom
import org.scalajs.dom.{Blob, BlobPropertyBag, HTMLAnchorElement, URL}
import typings.antd.anon.ScrollToFirstRowOnChange
import typings.antd.antdStrings.small
import typings.antd.cardMod.CardSize
import typings.antd.components.{Button, Card, Col, Dropdown, Layout, Menu, Row, Space, Table, Tooltip}
import typings.antd.tooltipMod.TooltipPropsWithTitle
import typings.antd.{antdBooleans, antdStrings}
import typings.rcTable
import typings.react.mod.{CSSProperties, RefAttributes}

import java.io.StringWriter
import scala.scalajs.js
import scala.scalajs.js.JSConverters.JSRichIterableOnce

object TransactionsView {
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

  case class Props(state: Main.State, refresh: Callback)

  val component =
    ScalaComponent
      .builder[Props]
      .initialState(false)
      .render { self =>
        val props          = self.props
        import props.state
        val itemMenuItems  = state.items.toTagMod(item => Menu.Item.withKey(item.itemId)(<.a(item.institution.name)))
        val removeItemMenu =
          Menu(itemMenuItems)
            .onClick { menuInfo =>
              Callback.traverseOption(state.items.find(_.itemId == menuInfo.key)) { item =>
                CallbackTo.confirm("Really remove " + item.institution.name + "?")
                  .flatMap {
                    case false => Callback.empty
                    case true  =>
                      Ajax("DELETE", s"/api/items/${item.itemId}").send.asAsyncCallback
                        .flatMapSync(_ => self.props.refresh)
                        .toCallback
                  }
              }
            }

        val downloadItemMenu =
          Menu(state.items.toTagMod(item => Menu.Item.withKey(item.itemId)(<.a(item.institution.name))))
            .onClick { menuInfo =>
              Callback.traverseOption(state.items.find(_.itemId == menuInfo.key)) { item =>
                Callback {
                  val transactions                                       =
                    state.info.transactions.flatMap(_.fold(_.toSeq, Seq(_)))
                      .filter { tx =>
                        state.info.accounts(tx.accountId).institution.institution_id == item.institution.institution_id
                      }
                      .sortBy(_.date)
                  val sw                                                 = new StringWriter()
                  implicit val headerEncoder: HeaderEncoder[Transaction] = new HeaderEncoder[Transaction] {
                    override val header =
                      Some(
                        List(
                          "Account ID",
                          "Date",
                          "Transaction ID",
                          "Description",
                          "Amount",
                          "Debit or Credit",
                          "Category",
                          "Type"
                        )
                      )

                    override val rowEncoder: RowEncoder[Transaction] = { (d: Transaction) =>
                      List(
                        d.accountId,
                        d.date.toString,
                        d.transactionId,
                        d.name,
                        d.amount.abs.toString,
                        if (d.amount > 0) "debit" else "credit",
                        d.category.mkString(" / "),
                        d.transactionType
                      )
                    }
                  }
                  sw.writeCsv(transactions, rfc.withHeader)
                  val a = dom.window.document.createElement("a").asInstanceOf[HTMLAnchorElement]
                  a.href =
                    URL.createObjectURL(new Blob(
                      js.Array(sw.toString),
                      new BlobPropertyBag { `type` = "text/csv;charset=utf-8" }
                    ))
                  a.setAttribute("download", item.institution.name + ".csv")
                  a.click()
                }
              }
            }

        Layout.style(CSSProperties().setPadding("24px 24px"))(
          Layout.Content(
            <.div(
              Space.direction(antdStrings.vertical)(
                Row(
                  Col.flex(antdStrings.auto)(
                    Card.size(CardSize.small)(
                      Space(
                        Button
                          .`type`(antdStrings.primary)("Add bank")
                          .onClick { _ =>
                            ajax[String]("/api/plaid-link-token").flatMapSync { plaidLinkToken =>
                              def doAdd(publicToken: String, institution: Institution) =
                                Ajax
                                  .post("/api/items")
                                  .send(AddItemRequest(publicToken, institution).asJson.spaces4)
                                  .asAsyncCallback
                                  .flatMapSync(_ => self.props.refresh)

                              Callback {
                                makePlaid(plaidLinkToken) { (publicToken, metadata) =>
                                  doAdd(
                                    publicToken,
                                    io.circe.scalajs.decodeJs[Institution](metadata.institution).toTry.get
                                  )
                                    .toCallback
                                }
                                  .open()
                              }
                            }.toCallback
                          },
                        <.div(
                          state.items
                            .flatMap(item => state.info.errors.get(item.itemId).map(item -> _))
                            .toTagMod { case (item, errors) =>
                              Button("Fix " + item.institution.name)
                                .title(errors.map(_.error_message).mkString("\n"))
                                .onClick { _ =>
                                  ajax[String]("/api/linkToken/" + item.itemId)
                                    .flatMapSync { token =>
                                      Callback {
                                        makePlaid(token)((_, _) => self.props.refresh)
                                          .open()
                                      }
                                    }
                                    .toCallback
                                }
                            }
                        ),
                        Dropdown(removeItemMenu.rawElement)
                          .triggerVarargs(antdStrings.click)(
                            Button.danger(true)(
                              Space(
                                "Remove",
                                <.i(^.cls := "fa fa-angle-down")
                              )
                            )
                          ),
                        Dropdown(downloadItemMenu.rawElement)
                          .triggerVarargs(antdStrings.click)(
                            Button(
                              Space(
                                "Download",
                                <.i(^.cls := "fa fa-angle-down")
                              )
                            )
                          )
                      )
                    )
                  )
                ),
                Table[TransactionsInfo.Item]()
                  .pagination(antdBooleans.`false`)
                  .dataSource(state.info.transactions.reverse.toJSArray)
                  .columnsVarargs(
                    columnType("date", "Date") { tx =>
                      Tooltip(
                        TooltipPropsWithTitle()
                          .setTitle(tx.transactionId)
                          .setChildren(tx.date.toString)
                          .combineWith(RefAttributes[Any]())
                      )
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
                    js.Dynamic.literal(y = "calc(100vh - 156px)")
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
        )
      }
      .build
}
