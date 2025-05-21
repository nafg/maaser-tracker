package maasertracker.js

import japgolly.scalajs.react.vdom.html_<^.*
import japgolly.scalajs.react.{Callback, ScalaComponent}

import maasertracker.{Transactions, TransactionsInfo}
import maasertracker.js.facades.ant
import maasertracker.js.facades.ant.Drawer

object TransactionRulePanel {
  case class Props(
      transaction: Option[Transactions.Item],
      visible: Boolean,
      transactionsInfo: TransactionsInfo,
      onClose: Callback
  )

  val component = ScalaComponent
    .builder[Props]
    .render_P { props =>
      val title = props.transaction.fold("Transaction Details") {
        case Right(tx)      => s"Transaction: ${tx.name}"
        case Left(transfer) => "Transfer Details"
      }

      ant.Drawer(
        onClose = props.onClose,
        placement = Drawer.Placement.Right,
        title = title,
        visible = props.visible,
        width = 400
      )(
        props.transaction.fold(EmptyVdom) {
          case Right(tx)      =>
            <.div(
              <.h3("Transaction Details"),
              <.p(s"ID: ${tx.transactionId}"),
              <.p(s"Date: ${tx.date}"),
              <.p(s"Description: ${tx.name}"),
              <.p(s"Amount: ${tx.amount}"),
              <.p(s"Category: ${tx.category.mkString(" > ")}"),
              <.h3("Matching Rules"),
              <.p("No matching rules found for this transaction."),
              <.div(
                ^.marginTop := "20px",
                ant.Button(
                  buttonType = ant.Button.Type.Primary,
                  onClick = _ => Callback.alert("Create Rule feature coming soon")
                )("Create Rule from This")
              )
            )
          case Left(transfer) =>
            <.div(
              <.h3("Transfer Details"),
              <.p(s"From: ${transfer.withdrawal.name} ($${transfer.withdrawal.amount})"),
              <.p(s"To: ${transfer.deposit.name} ($${transfer.deposit.amount})"),
              <.p("This transaction is part of a transfer. Currently, rules for transfers cannot be modified directly.")
            )
        }
      )
    }
    .build

  def apply(
      transaction: Option[Transactions.Item],
      visible: Boolean,
      transactionsInfo: TransactionsInfo,
      onClose: Callback
  ) = component(Props(transaction, visible, transactionsInfo, onClose))
}
