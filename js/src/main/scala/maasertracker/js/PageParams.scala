package maasertracker.js

import maasertracker.{Tags, TransactionMatcher}
import monocle.macros.GenLens

case class PageParams(accountFilters: Set[FilterItem[String]] = Set.empty,
                      categoryFilters: Set[FilterItem[List[String]]] = Set.empty,
                      amountFilters: Set[FilterItem[Double]] = Set.empty,
                      tagFilters: Set[FilterItem[Option[Tags.Value]]] = Set.empty,
                      matchName: Option[String],
                      matchMinAmount: Option[Double],
                      matchMaxAmount: Option[Double],
                      matchMatcher: Option[TransactionMatcher.Key],
                      sidePanelTransaction: Option[String] = None)

object PageParams {
  val lensAccountFilters       = GenLens[PageParams](_.accountFilters)
  val lensCategoryFilters      = GenLens[PageParams](_.categoryFilters)
  val lensAmountFilters        = GenLens[PageParams](_.amountFilters)
  val lensTagFilters           = GenLens[PageParams](_.tagFilters)
  val lensMatchName            = GenLens[PageParams](_.matchName)
  val lensMatchMinAmount       = GenLens[PageParams](_.matchMinAmount)
  val lensMatchMaxAmount       = GenLens[PageParams](_.matchMaxAmount)
  val lensMatchMatcher         = GenLens[PageParams](_.matchMatcher)
  val lensSidePanelTransaction = GenLens[PageParams](_.sidePanelTransaction)
}
