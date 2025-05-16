package maasertracker.js

import java.io.StringWriter

import scala.scalajs.js
import scala.scalajs.js.JSConverters.JSRichIterableOnce

import org.scalajs.dom
import org.scalajs.dom.{Blob, BlobPropertyBag, HTMLAnchorElement, URL}
import japgolly.scalajs.react.extra.Ajax
import japgolly.scalajs.react.extra.router.{BaseUrl, RouterCtl, RouterWithProps, RouterWithPropsConfigDsl, SetRouteVia}
import japgolly.scalajs.react.vdom.html_<^.*
import japgolly.scalajs.react.{Callback, CallbackTo, ScalaComponent}
import io.github.nafg.antd.facade.antd.anon.ScrollToFirstRowOnChange
import io.github.nafg.antd.facade.antd.antdStrings.small
import io.github.nafg.antd.facade.antd.components.{
  Button,
  Card,
  Col,
  Dropdown,
  Layout,
  Menu,
  Row,
  Space,
  Table,
  Tooltip
}
import io.github.nafg.antd.facade.antd.libCardMod.CardSize
import io.github.nafg.antd.facade.antd.libMenuMenuItemMod.MenuItemProps
import io.github.nafg.antd.facade.antd.libTooltipMod.TooltipPropsWithTitle
import io.github.nafg.antd.facade.antd.{antdBooleans, antdStrings}
import io.github.nafg.antd.facade.rcTable
import io.github.nafg.antd.facade.react.mod.{CSSProperties, RefAttributes}

import io.circe.syntax.EncoderOps
import kantan.csv.ops.*
import kantan.csv.{HeaderEncoder, RowEncoder, rfc}
import maasertracker.*
import monocle.Iso
import monocle.macros.GenLens

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

  case class Props(state: Main.State, refresh: Callback) {
    val dateColType =
      ColType("date", "Date")
        .withRenderEach { tx =>
          Tooltip(
            TooltipPropsWithTitle()
              .setTitle(tx.transactionId)
              .setChildren(tx.date.toString)
              .combineWith(RefAttributes[Any]())
          )
        }

    val accountColType  =
      ColType("account", "Account")
        .withRenderEach(t => accountLabel(state.info.accounts(t.accountId)))
        .filtering(_.accountId, State.lensAccountFilters)(
          state.info.accounts.groupBy(_._2.institution).map { case (institution, accounts) =>
            FilterItem(
              institution.institution_id == _,
              institution.name,
              accounts.values
                .toSeq
                .sortBy(_.account.name)
                .map(info => FilterItem(info.id == _, s"${info.account.name} (${info.account.subtype})"))
            )
          }
        )
    val nameColType     = ColType("name", "Name").withRenderEach(_.name)
    val categoryColType =
      ColType("category", "Category")
        .withRenderEach(_.category.mkString(" > "))
        .filtering(_.category, State.lensCategoryFilters)(
          state.categories.toJSArray.map { category =>
            FilterItem[List[String]](
              category == _,
              if (category.isEmpty) "None" else category.mkString(" > ")
            )
          }
        )
    val typeColType     = ColType("transactionType", "Type").withRenderEach(_.transactionType)
    val amountColType   =
      ColType(
        "amount",
        "Amount",
        { t =>
          val amount = -1 * t.fold(_.deposit.amount, _.amount)
          <.span(
            ^.color := (t match {
              case Right(tx) if tx.amount > 0 => "red"
              case Right(tx) if tx.amount < 0 => "green"
              case _                          => "gray"
            }),
            f"$$$amount%,.2f"
          )
        }
      ).filtering(t => t.amount, State.lensAmountFilters)(
        List(
          FilterItem(_ < 0, "Credit", hideTransfers = true),
          FilterItem(_ > 0, "Debit", hideTransfers = true)
        )
      )

    val tagColType =
      ColType("tag", "Tag")
        .withRender(t => state.info.tags.get(t.transactionId).mkString)
        .filtering(t => state.info.tags.get(t.transactionId), State.lensTagFilters)(
          FilterItem[Option[Tags.Value]](_.isEmpty, "No tag") +:
            Tags.values.toList.map(tag => FilterItem[Option[Tags.Value]](_.contains(tag), tag.toString))
        )

    val maaserBalanceColType =
      ColType("maaserBalance", "Maaser balance")
        .withRender { t =>
          f"$$${state.info.maaserBalances(t.transactionId)}%,.2f"
        }

    val filterColTypes = List(accountColType, categoryColType, amountColType, tagColType)
  }

  case class State(accountFilters: Set[FilterItem[String]] = Set.empty,
                   categoryFilters: Set[FilterItem[List[String]]] = Set.empty,
                   amountFilters: Set[FilterItem[Double]] = Set.empty,
                   tagFilters: Set[FilterItem[Option[Tags.Value]]] = Set.empty)
  object State {
    val lensAccountFilters  = GenLens[State](_.accountFilters)
    val lensCategoryFilters = GenLens[State](_.categoryFilters)
    val lensAmountFilters   = GenLens[State](_.amountFilters)
    val lensTagFilters      = GenLens[State](_.tagFilters)
  }

  private val component =
    ScalaComponent
      .builder[(Props, State, RouterCtl[State])]
      .render_P { case (props, state1, routerCtl) =>
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
                        .flatMapSync(_ => props.refresh)
                        .toCallback
                  }
              }
            }

        implicit val headerEncoder: HeaderEncoder[Transaction] = new HeaderEncoder[Transaction] {
          override val header: Option[Seq[String]] =
            Some(
              List(
                "Institution",
                "Account",
                "Account type",
                "Date",
                "Transaction ID",
                "Description",
                "Amount",
                "Debit or Credit",
                "Category",
                "Type",
                "Tag"
              )
            )

          override val rowEncoder: RowEncoder[Transaction] = { (d: Transaction) =>
            val accountInfo = state.info.accounts(d.accountId)
            List(
              accountInfo.institution.name,
              accountInfo.account.name,
              accountInfo.account.subtype,
              d.date.toString,
              d.transactionId,
              d.name,
              d.amount.abs.toString,
              if (d.amount > 0) "debit" else "credit",
              d.category.mkString(" / "),
              d.transactionType,
              state.info.tags.get(d.transactionId).fold("")(t => t.toString)
            )
          }
        }

        case class DownloadOption(institution: Option[Institution])
        val downloadOptions = state.items.map(item => DownloadOption(Some(item.institution))) :+ DownloadOption(None)

        val downloadItemMenu =
          Menu(downloadOptions.toReactFragment { option =>
            Menu.Item.withProps(MenuItemProps().setOnClick { menuInfo =>
              val transactions =
                option.institution match {
                  case None              => state.info.transactions.flatMap(_.toOption).sortBy(_.date)
                  case Some(institution) =>
                    state.info.transactions.flatMap(_.fold(_.toSeq, Seq(_)))
                      .filter { tx =>
                        state.info.accounts(tx.accountId).institution.institution_id == institution.institution_id
                      }
                      .sortBy(_.date)
                }

              Callback {
                val sw = new StringWriter()
                sw.writeCsv(transactions, rfc.withHeader)
                val a  = dom.window.document.createElement("a").asInstanceOf[HTMLAnchorElement]
                a.href =
                  URL.createObjectURL(new Blob(
                    js.Array(sw.toString),
                    new BlobPropertyBag {
                      `type` = "text/csv;charset=utf-8"
                    }
                  ))
                a.setAttribute("download", option.institution.fold("all")(_.name) + ".csv")
                a.click()
              }
            })(
              <.a(
                option.institution.fold("All - no transfers")(_.name)
              )
            )
          })

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
                                  .flatMapSync(_ => props.refresh)

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
                                        makePlaid(token)((_, _) => props.refresh)
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
                  .dataSource(state.info.transactions.toJSArray)
                  .onChange { case (_, filters, _, _) =>
                    routerCtl.set(props.filterColTypes.foldRight(state1)(_.handleOnChange(filters, _)))
                  }
                  .columnsVarargs(
                    List(
                      props.dateColType,
                      props.accountColType,
                      props.nameColType,
                      props.categoryColType,
                      props.typeColType,
                      props.amountColType,
                      props.tagColType,
                      props.maaserBalanceColType
                    )
                      .map(_.toAnt(state1))*
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

  private val baseUrl = BaseUrl.fromWindowOrigin_/

  private type QueryParamsMap = Map[String, Seq[String]]

  private def queryParamsStateIso(props: Props) =
    Iso[QueryParamsMap, State] { map =>
      props.filterColTypes.foldRight(State()) { case (filteringColType, state) =>
        map.get(filteringColType.key) match {
          case Some(keys) => filteringColType.keysToState(keys)(state)
          case None       => state
        }
      }
    } { state =>
      props.filterColTypes
        .map { filteringColType =>
          filteringColType.key -> filteringColType.stateToKeys(state).toSeq
        }
        .toMap
    }

  private val routerConfig = RouterWithPropsConfigDsl[QueryParamsMap, Props].buildConfig { dsl =>
    import dsl.*
    (dynamicRouteCT(queryToMultimap) ~>
      dynRenderRP { (state, routerCtl, props) =>
        val iso = queryParamsStateIso(props)
        component.apply((props, iso.get(state), routerCtl.contramap(iso.reverseGet)))
      })
      .notFound(redirectToPage(Map())(SetRouteVia.HistoryReplace))
  }

  val router = RouterWithProps(baseUrl, routerConfig)
}
