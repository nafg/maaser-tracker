package maasertracker.js

import java.io.StringWriter

import scala.scalajs.js
import scala.scalajs.js.JSConverters.JSRichIterableOnce

import org.scalajs.dom
import org.scalajs.dom.*
import japgolly.scalajs.react.extra.Ajax
import japgolly.scalajs.react.extra.router.{BaseUrl, RouterCtl, RouterWithProps, RouterWithPropsConfigDsl, SetRouteVia}
import japgolly.scalajs.react.vdom.html_<^.*
import japgolly.scalajs.react.{Callback, CallbackTo, ScalaComponent}
import io.github.nafg.antd.facade.antd.antdStrings.small
import io.github.nafg.antd.facade.antd.libCardMod.CardSize
import io.github.nafg.antd.facade.antd.{antdBooleans, antdStrings}
import io.github.nafg.antd.facade.react.mod.CSSProperties

import kantan.csv.ops.*
import kantan.csv.{HeaderEncoder, RowEncoder, rfc}
import maasertracker.*
import maasertracker.js.Facades.Ant
import monocle.Iso
import monocle.macros.GenLens

object TransactionsView {
  private def accountNameParts(acct: AccountInfo) = {
    val i = acct.institution.name
    val a = acct.account.name.replaceAll(i, "").trim
    val t = acct.account.subtype
    (i, a, t)
  }

  private def accountLabel(acct: AccountInfo) = {
    val (i, a, t) = accountNameParts(acct)
    val a1        = a match {
      case "" => ""
      case a0 => ": " + a0
    }
    s"$i$a1 ($t)"
  }

  case class Props(state: Main.State, refresh: Callback) {
    val dateColType =
      ColType("date", "Date")
        .withRenderEach { tx =>
          Ant.Tooltip(title = tx.transactionId)(
            tx.date.toString
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

  private def refreshItem(item: PlaidItem) =
    ajaxGet[String]("/api/linkToken/" + item.itemId)
      .flatMap(Plaid.makeAndOpen)

  private def addBank() =
    ajaxGet[String]("/api/plaid-link-token")
      .flatMap(Plaid.makeAndOpen)
      .flatMap(result => ajaxPost[Unit]("/api/items", AddItemRequest(result.publicToken, result.metadata.institution)))

  private def removeItem(item: PlaidItem) = Ajax("DELETE", s"/api/items/${item.itemId}").send.asAsyncCallback

  private val component =
    ScalaComponent
      .builder[(Props, State, RouterCtl[State])]
      .render_P { case (props, state1, routerCtl) =>
        import props.state

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

        Ant.Layout(style = CSSProperties().setPadding("24px 24px"))(
          Ant.Layout.Content(
            <.div(
              Ant.Space(direction = antdStrings.vertical)(
                Ant.Row(
                  Ant.Col(flex = antdStrings.auto)(
                    Ant.Card(size = CardSize.small)(
                      Ant.Space()(
                        Ant.Button(
                          buttonType = antdStrings.primary,
                          onClick = _ => addBank().flatMapSync(_ => props.refresh).toCallback
                        )("Add bank"),
                        <.div(
                          state.items
                            .flatMap(item => state.info.errors.get(item.itemId).map(item -> _))
                            .toTagMod { case (item, errors) =>
                              Ant.Button(
                                onClick = _ => refreshItem(item).flatMapSync(_ => props.refresh).toCallback,
                                title = errors.map(_.error_message).mkString("\n")
                              )(s"Fix ${item.institution.name}")
                            }
                        ),
                        Ant.Dropdown(antdStrings.click)(
                          Ant.Button(danger = true)(Ant.Space()("Remove", <.i(^.cls := "fa fa-angle-down")))
                        )(
                          state.items.map { item =>
                            Ant.Dropdown.Item(item.itemId)(item.institution.name) {
                              CallbackTo.confirm("Really remove " + item.institution.name + "?")
                                .flatMap {
                                  case false => Callback.empty
                                  case true  => removeItem(item).flatMapSync(_ => props.refresh).toCallback
                                }
                            }
                          }
                        ),
                        Ant.Dropdown(antdStrings.click)(
                          Ant.Button()(Ant.Space()("Download", <.i(^.cls := "fa fa-angle-down")))
                        )(
                          downloadOptions.map { option =>
                            Ant.Dropdown.Item(option.institution.fold("all")(_.institution_id))(
                              option.institution.fold("All - no transfers")(_.name)
                            )(Callback {
                              val transactions =
                                option.institution match {
                                  case None              => state.info.transactions.flatMap(_.toOption).sortBy(_.date)
                                  case Some(institution) =>
                                    state.info.transactions.flatMap(_.fold(_.toSeq, Seq(_)))
                                      .filter { tx =>
                                        state.info.accounts(
                                          tx.accountId
                                        ).institution.institution_id == institution.institution_id
                                      }
                                      .sortBy(_.date)
                                }
                              val sw           = new StringWriter()
                              sw.writeCsv(transactions, rfc.withHeader)
                              val a            = dom.window.document.createElement("a").asInstanceOf[HTMLAnchorElement]
                              a.href =
                                URL.createObjectURL(new Blob(
                                  js.Array(sw.toString),
                                  new BlobPropertyBag {
                                    `type` = "text/csv;charset=utf-8"
                                  }
                                ))
                              a.setAttribute("download", option.institution.fold("all")(_.name) + ".csv")
                              a.click()
                            })
                          }
                        )
                      )
                    )
                  )
                ),
                Ant.Table(state.info.transactions)(
                  columns =
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
                      .map(_.toAnt(state1)),
                  onChange = { case (_, filters, _, _) =>
                    routerCtl.set(props.filterColTypes.foldRight(state1)(_.handleOnChange(filters, _)))
                  },
                  pagination = antdBooleans.`false`,
                  rowClassName = {
                    case (Right(tx), _) =>
                      state.info.tags.get(tx.transactionId) match {
                        case None      => ""
                        case Some(tag) => "tag-" + tag.toString
                      }
                    case _              => ""
                  },
                  rowKey = {
                    case Right(tx)                => tx.transactionId
                    case Left(Transfer(tx1, tx2)) => tx1.transactionId + "->" + tx2.transactionId
                  },
                  scroll = Ant.Table.ScrollConfig(y = "calc(100vh - 156px)"),
                  size = small
                )
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
