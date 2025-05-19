package maasertracker.js

import japgolly.scalajs.react.Callback
import japgolly.scalajs.react.ReactMonocle.MonocleReactExt_StateSnapshot
import japgolly.scalajs.react.vdom.html_<^.*

import maasertracker.js.facades.ant
import maasertracker.{AccountInfo, Tags}

case class Props(state: Main.State, refresh: Callback) {
  private def formatDollars(amount: Double) = f"$$$amount%,.2f"

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

  val dateColType =
    ColType("date", "Date")
      .withRenderEach { tx =>
        ant.Tooltip(title = tx.transactionId)(
          tx.date.toString
        )
      }

  val accountColType =
    ColType("account", "Account")
      .withRenderEach(t => accountLabel(state.info.accounts(t.accountId)))
      .filtering(_.accountId, PageParams.lensAccountFilters)(
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

  val nameColType = ColType("name", "Name").withRenderEach(_.name)

  val categoryColType =
    ColType("category", "Category")
      .withRenderEach(_.category.mkString(" > "))
      .filtering(_.category, PageParams.lensCategoryFilters)(
        state.categories.map { category =>
          FilterItem[List[String]](
            category == _,
            if (category.isEmpty) "None" else category.mkString(" > ")
          )
        }
      )

  val typeColType = ColType("transactionType", "Type").withRenderEach(_.transactionType)

  val amountColType =
    ColType("amount", "Amount")
      .withRender(
        single = { t => _ =>
          <.span(
            if (t.amount > 0)
              ^.color.red
            else if (t.amount < 0)
              ^.color.green
            else
              ^.color.gray,
            formatDollars(-t.amount)
          )
        },
        transfer = { t => _ =>
          <.span(^.color.gray, formatDollars(t.withdrawal.amount))
        }
      )
      .filtering(t => t.amount, PageParams.lensAmountFilters)(
        List(
          FilterItem(_ < 0, "Credit", hideTransfers = true),
          FilterItem(_ > 0, "Debit", hideTransfers = true)
        )
      )

  val tagColType =
    ColType("tag", "Tag")
      .withRender { t => pageParams =>
        ant.Button(
          buttonType = ant.Button.Type.Link,
          onClick = _ =>
            pageParams.setStateL(PageParams.lensSidePanelTransaction)(Some(t.transactionId))
        )(
          state.info.tags.get(t.transactionId).mkString
        )
      }
      .filtering(t => state.info.tags.get(t.transactionId), PageParams.lensTagFilters)(
        FilterItem[Option[Tags.Value]](_.isEmpty, "No tag") +:
          Tags.values.toList.map(tag => FilterItem[Option[Tags.Value]](_.contains(tag), tag.toString))
      )

  val maaserBalanceColType =
    ColType("maaserBalance", "Maaser balance")
      .withRender { t => _ =>
        formatDollars {
          state.info.maaserBalances(t.transactionId)
        }
      }

  val filterColTypes = List(accountColType, categoryColType, amountColType, tagColType)
}
