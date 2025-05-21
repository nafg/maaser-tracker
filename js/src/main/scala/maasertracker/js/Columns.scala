package maasertracker.js

import japgolly.scalajs.react.ReactMonocle.MonocleReactExt_StateSnapshot
import japgolly.scalajs.react.vdom.html_<^.*

import maasertracker.js.facades.ant
import maasertracker.*

class Columns(transactionsInfo: TransactionsInfo, refresh: Refresher) {
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
      .withRenderEach(t => accountLabel(transactionsInfo.transactions.accounts.byId(t.accountId)))
      .filtering(_.accountId)(FilterSpecs.accountFilterSpec)

  val nameColType = ColType("name", "Name").withRenderEach(_.name)

  val categoryColType =
    ColType("category", "Category")
      .withRenderEach(_.category.mkString(" > "))
      .filtering(_.category)(FilterSpecs.categoryFilterSpec)

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
      .filtering(_.amount)(FilterSpecs.amountFilterSpec)

  val tagColType =
    ColType("tag", "Tag")
      .withRender { tx => pageParams =>
        def idMatcher =
          TransactionMatcher(
            id = Some(tx.transactionId),
            institution = None,
            description = None,
            category = None,
            minAmount = None,
            maxAmount = None
          )

        def addIdRule(t: Tags.Value) = Api.MatchRules.add(Kind.forTag(t), idMatcher)

        val manageRulesItem =
          ant.Dropdown.Item("Manage rules")("Manage rules") {
            pageParams.setStateL(PageParams.lensSidePanelTransaction)(Some(tx.transactionId))
          }

        def dropdown(label: String)(firstItems: Iterable[ant.Dropdown.Child]*) = {
          val firstItemsFlat = firstItems.toList.flatten
          ant.Dropdown.hover(ant.Button()(label))(
            if (firstItemsFlat.isEmpty)
              List(manageRulesItem)
            else
              firstItemsFlat ++ List(ant.Dropdown.Divider, manageRulesItem)
          )
        }

        transactionsInfo.tagsAndMatchers.get(tx.transactionId) match {
          case None                 =>
            dropdown("No tag")(
              Tags.values.toList.map { tag =>
                ant.Dropdown.Item(tag.toString)(<.span("Set to ", <.b(tag.toString))) {
                  (addIdRule(tag) >> refresh.reloadMatchers).toCallback
                }
              }
            )
          case Some((tag, matcher)) =>
            def deleteMatcher() = Api.MatchRules.delete(Kind.forTag(tag), matcher)

            dropdown(tag.toString)(
              if (matcher.id.isEmpty) Nil
              else
                Tags.values.filterNot(_ == tag).toList.map { t =>
                  ant.Dropdown.Item(t.toString)(<.span("Change to ", <.b(t.toString))) {
                    (deleteMatcher() >> addIdRule(t) >> refresh.reloadMatchers).toCallback
                  }
                } ++
                  Option.when(matcher.id.isDefined) {
                    ant.Dropdown.Item("Remove tag")("Remove tag") {
                      (deleteMatcher() >> refresh.reloadMatchers).toCallback
                    }
                  }
            )
        }
      }
      .filtering(t => transactionsInfo.tags.get(t.transactionId))(FilterSpecs.tagFilterSpec)

  val maaserBalanceColType =
    ColType("maaserBalance", "Maaser balance")
      .withRender { t => _ =>
        formatDollars {
          transactionsInfo.maaserBalances(t.transactionId)
        }
      }
}
