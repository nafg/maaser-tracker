package maasertracker.js

import japgolly.scalajs.react.extra.StateSnapshot
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.html_<^.*
import japgolly.scalajs.react.{Callback, CallbackTo, ScalaComponent}

import maasertracker.*
import maasertracker.js.facades.ant

object TransactionsView {
  private def refreshItem(item: PlaidItem) =
    Api.LinkTokens.get(item)
      .flatMap(Plaid.makeAndOpen)

  private def addBank() =
    Api.LinkTokens.get
      .flatMap(Plaid.makeAndOpen)
      .flatMap(result => Api.Items.add(result.publicToken, result.metadata.institution))

  private def removeItem(item: PlaidItem) = Api.Items.delete(item)

  case class Props(routerProps: Router.Props, pageParams: PageParams, routerCtl: RouterCtl[PageParams]) {
    def refresh      = routerProps.refresh
    def info         = routerProps.info
    lazy val columns = new Columns(info, refresh)
  }

  val component = {
    ScalaComponent
      .builder[Props]
      .render_P { case props @ Props(_, pageParams, routerCtl) =>
        import props.columns

        val transactionsInfo = props.info

        val pageParamsStateSnapshot =
          StateSnapshot(pageParams) {
            case (Some(s), _) => routerCtl.set(s)
            case _            => Callback.empty
          }

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
                          onClick = _ => (addBank() >> props.refresh.reloadTransactions).toCallback
                        )("Add bank"),
                        <.div(
                          transactionsInfo.plaidItems
                            .flatMap(item => transactionsInfo.plaidData.errors.get(item.itemId).map(item -> _))
                            .toTagMod { case (item, errors) =>
                              ant.Button(
                                onClick = _ => (refreshItem(item) >> props.refresh.reloadTransactions).toCallback,
                                title = errors.map(_.error_message).mkString("\n")
                              )(s"Fix ${item.institution.name}")
                            }
                        ),
                        ant.Dropdown.click(
                          ant.Button(danger = true)(ant.Space()("Remove", <.i(^.cls := "fa fa-angle-down")))
                        )(
                          transactionsInfo.plaidItems.map { item =>
                            ant.Dropdown.Item(item.itemId)(item.institution.name) {
                              CallbackTo.confirm("Really remove " + item.institution.name + "?")
                                .flatMap {
                                  case false => Callback.empty
                                  case true  => (removeItem(item) >> props.refresh.reloadTransactions).toCallback
                                }
                            }
                          }
                        ),
                        DownloadsDropdown.downloadDropdown(transactionsInfo, transactionsInfo.plaidItems)
                      )
                    )
                  )
                ),
                ant.Table(transactionsInfo.combinedItems)(
                  columns =
                    List(
                      columns.dateColType,
                      columns.accountColType,
                      columns.nameColType,
                      columns.categoryColType,
                      columns.typeColType,
                      columns.amountColType,
                      columns.tagColType,
                      columns.maaserBalanceColType
                    )
                      .map(ColType.toAnt(transactionsInfo, pageParamsStateSnapshot, _)),
                  onChange = {
                    case (_, filters, _, _) =>
                      routerCtl.set(
                        FilterSpec.applyFilters(transactionsInfo, filters, FilterSpecs.filterSpecs)(pageParams)
                      )
                  },
                  pagination = ant.Table.Pagination.False,
                  rowClassName = {
                    case (Right(tx), _) =>
                      transactionsInfo.tags.get(tx.transactionId) match {
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
                      transactionsInfo.combinedItems.collectFirst {
                        case item @ Right(tx) if tx.transactionId == id                                            =>
                          item
                        case item @ Left(Transfer(tx1, tx2)) if tx1.transactionId == id || tx2.transactionId == id =>
                          item
                      }
                    },
                  onClose = routerCtl.set(PageParams.lensSidePanelTransaction.replace(None)(pageParams)),
                  visible = pageParams.sidePanelTransaction.nonEmpty,
                  transactionsInfo = transactionsInfo
                )
              )
            )
          )
        )
      }
  }.build

}
