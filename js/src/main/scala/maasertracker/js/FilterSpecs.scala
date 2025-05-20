package maasertracker.js

import maasertracker.Tags

object FilterSpecs {
  val accountFilterSpec =
    FilterSpec("account", PageParams.lensAccountFilters) { state =>
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
    }

  val categoryFilterSpec =
    FilterSpec("category", PageParams.lensCategoryFilters) { state =>
      state.categories.map { category =>
        FilterItem[List[String]](
          category == _,
          if (category.isEmpty) "None" else category.mkString(" > ")
        )
      }
    }

  val amountFilterSpec =
    FilterSpec("amount", PageParams.lensAmountFilters) { _ =>
      List(
        FilterItem(_ < 0, "Credit", hideTransfers = true),
        FilterItem(_ > 0, "Debit", hideTransfers = true)
      )
    }

  val tagFilterSpec =
    FilterSpec("tag", PageParams.lensTagFilters) { _ =>
      FilterItem[Option[Tags.Value]](_.isEmpty, "No tag") +:
        Tags.values.toList.map(tag => FilterItem[Option[Tags.Value]](_.contains(tag), tag.toString))
    }

  val filterSpecs: Seq[FilterSpec[PageParams, ?]] =
    List(accountFilterSpec, categoryFilterSpec, amountFilterSpec, tagFilterSpec)
}
