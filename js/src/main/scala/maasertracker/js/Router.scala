package maasertracker.js

import slick.additions.entity.EntityKey
import japgolly.scalajs.react.extra.router.{BaseUrl, RouterWithProps, RouterWithPropsConfigDsl, SetRouteVia}

import maasertracker.TransactionsInfo

object Router {
  private val baseUrl = BaseUrl.fromWindowOrigin_/

  private type QueryParamsMap = Seq[(String, String)]

  private def pageParamsToQueryParams(info: TransactionsInfo, pageParams: PageParams): QueryParamsMap =
    pageParams.matchName.toSeq.map("name"       -> _) ++
      pageParams.matchMinAmount.toSeq.map("min" -> _.toString) ++
      pageParams.matchMaxAmount.toSeq.map("max" -> _.toString) ++
      pageParams.matchMatcher.toSeq.map("matcher" -> _.key.toString) ++
      pageParams.sidePanelTransaction.toSeq.map("show" -> _) ++
      FilterSpecs.filterSpecs
        .flatMap { filterSpec =>
          filterSpec.pageParamsToKeys(info, pageParams).toSeq.map(filterSpec.key -> _)
        }

  private def queryParamsToPageParams(info: TransactionsInfo, map: QueryParamsMap): PageParams =
    FilterSpecs.filterSpecs
      .foldRight(
        PageParams(
          matchName = map.collect { case ("name", v) => v }.lastOption,
          matchMinAmount = map.collect { case ("min", v) => v.toDouble }.lastOption,
          matchMaxAmount = map.collect { case ("max", v) => v.toDouble }.lastOption,
          matchMatcher = map.collect { case ("matcher", v) => EntityKey(v.toLong) }.lastOption,
          sidePanelTransaction = map.collect { case ("show", v) => v }.lastOption
        )
      ) {
        case (filterSpec, pageParams) =>
          filterSpec.keysToPageParams(info, map.collect { case (k, v) if k == filterSpec.key => v })(
            pageParams
          )
      }

  case class Props(info: TransactionsInfo, refresh: Refresher)

  private val routerConfig = RouterWithPropsConfigDsl[QueryParamsMap, Props].buildConfig { dsl =>
    import dsl.*

    (dynamicRouteCT(queryToSeq) ~>
      dynRenderRP { (map, routerCtl, props) =>
        TransactionsView.component(
          TransactionsView.Props(
            props,
            queryParamsToPageParams(props.info, map),
            routerCtl.contramap(pageParams => pageParamsToQueryParams(props.info, pageParams))
          )
        )
      })
      .notFound(redirectToPage(Seq())(SetRouteVia.HistoryReplace))
  }

  val router = RouterWithProps(baseUrl, routerConfig)
}
