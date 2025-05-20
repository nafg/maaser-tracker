package maasertracker.js

import japgolly.scalajs.react.Callback
import japgolly.scalajs.react.extra.router.{BaseUrl, RouterWithProps, RouterWithPropsConfigDsl, SetRouteVia}

object Router {
  private val baseUrl = BaseUrl.fromWindowOrigin_/

  private type QueryParamsMap = Seq[(String, String)]

  private def pageParamsToQueryParams(state: Main.State, pageParams: PageParams): QueryParamsMap =
    pageParams.sidePanelTransaction.toSeq.map("show" -> _) ++
      FilterSpecs.filterSpecs
        .flatMap { filterSpec =>
          filterSpec.pageParamsToKeys(state, pageParams).toSeq.map(filterSpec.key -> _)
        }

  private def queryParamsToPageParams(state: Main.State, map: QueryParamsMap): PageParams =
    FilterSpecs.filterSpecs
      .foldRight(PageParams(sidePanelTransaction = map.collect { case ("show", v) => v }.lastOption)) {
        case (filterSpec, pageParams) =>
          filterSpec.keysToPageParams(state, map.collect { case (k, v) if k == filterSpec.key => v })(
            pageParams
          )
      }

  case class Props(state: Main.State, refresh: Callback)

  private val routerConfig = RouterWithPropsConfigDsl[QueryParamsMap, Props].buildConfig { dsl =>
    import dsl.*

    (dynamicRouteCT(queryToSeq) ~>
      dynRenderRP { (map, routerCtl, props) =>
        TransactionsView.component(
          TransactionsView.Props(
            props,
            queryParamsToPageParams(props.state, map),
            routerCtl.contramap(pageParams => pageParamsToQueryParams(props.state, pageParams))
          )
        )
      })
      .notFound(redirectToPage(Seq())(SetRouteVia.HistoryReplace))
  }

  val router = RouterWithProps(baseUrl, routerConfig)
}
