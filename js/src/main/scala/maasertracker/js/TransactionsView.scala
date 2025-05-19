package maasertracker.js

import japgolly.scalajs.react.extra.router.{BaseUrl, RouterCtl, RouterWithProps, RouterWithPropsConfigDsl, SetRouteVia}
import japgolly.scalajs.react.vdom.html_<^.*
import japgolly.scalajs.react.{Callback, CallbackTo, ScalaComponent}

import maasertracker.*
import maasertracker.js.facades.ant
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

        ant.Layout()(
          ^.padding := "24px 24px",
          ant.Layout.Content(
            <.div(
              ant.Space(direction = ant.Space.Direction.Vertical)(
                ant.Row(
                  ant.Col(flex = ant.Col.Flex.Auto)(
                    ant.Card(size = ant.Card.Size.Small)(
                      ant.Space()(
                        ant.Button(
                          buttonType = ant.Button.Type.Primary,
                          onClick = _ => addBank().flatMapSync(_ => props.refresh).toCallback
                        )("Add bank"),
                        <.div(
                          state.items
                            .flatMap(item => state.info.errors.get(item.itemId).map(item -> _))
                            .toTagMod { case (item, errors) =>
                              ant.Button(
                                onClick = _ => refreshItem(item).flatMapSync(_ => props.refresh).toCallback,
                                title = errors.map(_.error_message).mkString("\n")
                              )(s"Fix ${item.institution.name}")
                            }
                        ),
                        ant.Dropdown(ant.Dropdown.Trigger.Click)(
                          ant.Button(danger = true)(ant.Space()("Remove", <.i(^.cls := "fa fa-angle-down")))
                        )(
                          state.items.map { item =>
                            ant.Dropdown.Item(item.itemId)(item.institution.name) {
                              CallbackTo.confirm("Really remove " + item.institution.name + "?")
                                .flatMap {
                                  case false => Callback.empty
                                  case true  => removeItem(item).flatMapSync(_ => props.refresh).toCallback
                                }
                            }
                          }
                        ),
                        DownloadsDropdown.downloadDropdown(state.info, state.items)
                      )
                    )
                  )
                ),
                ant.Table(state.info.transactions)(
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
                  pagination = ant.Table.Pagination.False,
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
                  scroll = ant.Table.ScrollConfig(y = "calc(100vh - 156px)"),
                  size = ant.Table.Size.Small
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
