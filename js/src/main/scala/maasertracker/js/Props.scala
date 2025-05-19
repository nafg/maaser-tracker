package maasertracker.js

import scala.scalajs.js.JSConverters.JSRichIterableOnce

import japgolly.scalajs.react.Callback
import japgolly.scalajs.react.vdom.html_<^.*

import maasertracker.js.facades.ant
import maasertracker.{AccountInfo, Tags}

case class Props(state: Main.State, refresh: Callback) {
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
      .filtering(_.accountId, TransactionsView.State.lensAccountFilters)(
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
      .filtering(_.category, TransactionsView.State.lensCategoryFilters)(
        state.categories.toJSArray.map { category =>
          FilterItem[List[String]](
            category == _,
            if (category.isEmpty) "None" else category.mkString(" > ")
          )
        }
      )

  val typeColType = ColType("transactionType", "Type").withRenderEach(_.transactionType)

  val amountColType =
    ColType(
      "amount",
      "Amount",
      { t =>
        val amount = -1 * t.fold(_.deposit.amount, _.amount)
        <.span(
          ^.color := (t match {
            case Right(tx) if tx.amount > 0 => "red"
            case Right(tx) if tx.amount < 0 => "green"
            case _                          => "gray"
          }),
          f"$$$amount%,.2f"
        )
      }
    ).filtering(t => t.amount, TransactionsView.State.lensAmountFilters)(
      List(
        FilterItem(_ < 0, "Credit", hideTransfers = true),
        FilterItem(_ > 0, "Debit", hideTransfers = true)
      )
    )

  val tagColType =
    ColType("tag", "Tag")
      .withRender(t => state.info.tags.get(t.transactionId).mkString)
      .filtering(t => state.info.tags.get(t.transactionId), TransactionsView.State.lensTagFilters)(
        FilterItem[Option[Tags.Value]](_.isEmpty, "No tag") +:
          Tags.values.toList.map(tag => FilterItem[Option[Tags.Value]](_.contains(tag), tag.toString))
      )

  val maaserBalanceColType =
    ColType("maaserBalance", "Maaser balance")
      .withRender { t =>
        f"$$${state.info.maaserBalances(t.transactionId)}%,.2f"
      }

  val filterColTypes = List(accountColType, categoryColType, amountColType, tagColType)
}
