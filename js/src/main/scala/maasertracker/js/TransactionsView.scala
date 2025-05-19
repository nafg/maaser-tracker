package maasertracker.js

import java.io.StringWriter

import scala.scalajs.js

import org.scalajs.dom
import org.scalajs.dom.*
import japgolly.scalajs.react.extra.router.{BaseUrl, RouterCtl, RouterWithProps, RouterWithPropsConfigDsl, SetRouteVia}
import japgolly.scalajs.react.vdom.html_<^.*
import japgolly.scalajs.react.{Callback, CallbackTo, ScalaComponent}

import kantan.csv.ops.*
import kantan.csv.{HeaderEncoder, RowEncoder, rfc}
import maasertracker.*
import maasertracker.js.Facades.Ant
import monocle.Iso
import monocle.macros.GenLens

object TransactionsView {
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
    Api.getLinkToken(item)
      .flatMap(Plaid.makeAndOpen)

  private def addBank() =
    Api.getPlaidLinkToken
      .flatMap(Plaid.makeAndOpen)
      .flatMap(result => Api.addItem(result.publicToken, result.metadata.institution))

  private def removeItem(item: PlaidItem) = Api.deleteItem(item)

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

        Ant.Layout()(
          ^.padding := "24px 24px",
          Ant.Layout.Content(
            <.div(
              Ant.Space(direction = Ant.Space.Direction.Vertical)(
                Ant.Row(
                  Ant.Col(flex = Ant.Col.Flex.Auto)(
                    Ant.Card(size = Ant.Card.Size.Small)(
                      Ant.Space()(
                        Ant.Button(
                          buttonType = Ant.Button.Type.Primary,
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
                        Ant.Dropdown(Ant.Dropdown.Trigger.Click)(
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
                        Ant.Dropdown(Ant.Dropdown.Trigger.Click)(
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
                  pagination = Ant.Table.Pagination.False,
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
                  size = Ant.Table.Size.Small
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
