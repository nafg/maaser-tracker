package maasertracker.js

import japgolly.scalajs.react.extra.StateSnapshot
import japgolly.scalajs.react.extra.router.{BaseUrl, RouterCtl, RouterWithProps, RouterWithPropsConfigDsl, SetRouteVia}
import japgolly.scalajs.react.vdom.html_<^.*
import japgolly.scalajs.react.{Callback, CallbackTo, ScalaComponent}

import maasertracker.*
import maasertracker.js.facades.ant

object TransactionsView {
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
      .builder[(Props, PageParams, RouterCtl[PageParams])]
      .render_P { case (props, pageParams, routerCtl) =>
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
                      .map(_.toAnt(StateSnapshot(pageParams) {
                        case (Some(s), _) => routerCtl.set(s)
                        case _            => Callback.empty
                      })),
                  onChange = { case (_, filters, _, _) =>
                    routerCtl.set(props.filterColTypes.foldRight(pageParams)(_.handleOnChange(filters, _)))
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
                ),
                TransactionRulePanel(
                  transaction =
                    pageParams.sidePanelTransaction.flatMap { id =>
                      state.info.transactions.collectFirst {
                        case item @ Right(tx) if tx.transactionId == id => item
                        case item @ Left(Transfer(tx1, tx2)) if tx1.transactionId == id || tx2.transactionId == id =>
                          item
                      }
                    },
                  onClose = routerCtl.set(PageParams.lensSidePanelTransaction.replace(None)(pageParams)),
                  visible = pageParams.sidePanelTransaction.nonEmpty,
                  transactionsInfo = state.info
                )
              )
            )
          )
        )
      }
      .build

  private val baseUrl = BaseUrl.fromWindowOrigin_/

  private type QueryParamsMap = Seq[(String, String)]

  private val routerConfig = RouterWithPropsConfigDsl[QueryParamsMap, Props].buildConfig { dsl =>
    import dsl.*
    (dynamicRouteCT(queryToSeq) ~>
      dynRenderRP { (map, routerCtl, props) =>
        component.apply((
          props,
          props.filterColTypes
            .foldRight(PageParams(sidePanelTransaction = map.collect { case ("show", v) => v }.lastOption)) {
              case (filteringColType, pageParams) =>
                filteringColType.keysToPageParams(map.collect { case (k, v) if k == filteringColType.key => v })(
                  pageParams
                )
            },
          routerCtl.contramap { pageParams =>
            pageParams.sidePanelTransaction.toSeq.map("show" -> _) ++
              props.filterColTypes
                .flatMap { filteringColType =>
                  filteringColType.pageParamsToKeys(pageParams).toSeq.map(filteringColType.key -> _)
                }
          }
        ))
      })
      .notFound(redirectToPage(Seq())(SetRouteVia.HistoryReplace))
  }

  val router = RouterWithProps(baseUrl, routerConfig)
}
