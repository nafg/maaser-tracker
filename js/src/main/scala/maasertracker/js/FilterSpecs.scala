package maasertracker.js

import maasertracker.Tags

object FilterSpecs {
  val accountFilterSpec =
    FilterSpec("account", PageParams.lensAccountFilters) { info =>
      info.plaidData.accounts.accounts.map { case (item, accounts) =>
        FilterItem[String](
          item.institution.name,
          accounts
            .sortBy(_.account.name)
            .map(info => FilterItem(s"${info.account.name} (${info.account.subtype})")(info.id == _))
        ) { id =>
          item.institution.institution_id == id
        }
      }
    }

  val categoryFilterSpec =
    FilterSpec("category", PageParams.lensCategoryFilters) { info =>
      info.categories.map { category =>
        FilterItem[List[String]](if (category.isEmpty) "None" else category.mkString(" > "))(category == _)
      }
    }

  val amountFilterSpec =
    FilterSpec("amount", PageParams.lensAmountFilters) { _ =>
      List(
        FilterItem("Credit", hideTransfers = true)(_ < 0),
        FilterItem("Debit", hideTransfers = true)(_ > 0)
      )
    }

  val tagFilterSpec =
    FilterSpec("tag", PageParams.lensTagFilters) { _ =>
      FilterItem[Option[Tags.Value]]("No tag")(_.isEmpty) +:
        Tags.values.toList.map(tag => FilterItem[Option[Tags.Value]](tag.toString)(_.contains(tag)))
    }

  val filterSpecs: Seq[FilterSpec[PageParams, ?]] =
    List(accountFilterSpec, categoryFilterSpec, amountFilterSpec, tagFilterSpec)
}
