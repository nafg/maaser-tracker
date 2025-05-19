package maasertracker.js

import java.io.StringWriter

import scala.scalajs.js

import org.scalajs.dom
import org.scalajs.dom.{Blob, BlobPropertyBag, HTMLAnchorElement, URL}
import japgolly.scalajs.react.Callback
import japgolly.scalajs.react.vdom.html_<^.*

import kantan.csv.ops.toCsvOutputOps
import kantan.csv.{HeaderEncoder, RowEncoder, rfc}
import maasertracker.*
import maasertracker.js.Facades.Ant

object DownloadsDropdown {
  private class TransactionHeaderEncoder(accounts: Map[String, AccountInfo], tags: Map[String, Tags.Value])
      extends HeaderEncoder[Transaction] {
    override val header: Option[Seq[String]] =
      Some(
        List(
          "Institution",
          "Account",
          "Account type",
          "Date",
          "Transaction ID",
          "Description",
          "Amount",
          "Debit or Credit",
          "Category",
          "Type",
          "Tag"
        )
      )

    override val rowEncoder: RowEncoder[Transaction] = { (d: Transaction) =>
      val accountInfo = accounts(d.accountId)
      List(
        accountInfo.institution.name,
        accountInfo.account.name,
        accountInfo.account.subtype,
        d.date.toString,
        d.transactionId,
        d.name,
        d.amount.abs.toString,
        if (d.amount > 0) "debit" else "credit",
        d.category.mkString(" / "),
        d.transactionType,
        tags.get(d.transactionId).fold("")(t => t.toString)
      )
    }
  }

  private def download(transactions: Seq[Transaction], basename: String)(implicit
      headerEncoder: HeaderEncoder[Transaction]) =
    Callback {
      val sw = new StringWriter()
      sw.writeCsv(transactions.sortBy(_.date), rfc.withHeader)
      val a  = dom.window.document.createElement("a").asInstanceOf[HTMLAnchorElement]
      a.href =
        URL.createObjectURL(
          new Blob(
            js.Array(sw.toString),
            new BlobPropertyBag {
              `type` = "text/csv;charset=utf-8"
            }
          )
        )
      a.setAttribute("download", basename + ".csv")
      a.click()
    }

  def downloadDropdown(transactionsInfo: TransactionsInfo, items: Seq[PlaidItem]): VdomElement = {
    implicit val headerEncoder = new TransactionHeaderEncoder(transactionsInfo.accounts, transactionsInfo.tags)

    def downloadInstitutionItem(item: PlaidItem) =
      Ant.Dropdown.Item(item.institution.institution_id)(item.institution.name) {
        download(
          transactionsInfo.transactions
            .flatMap(_.fold(_.toSeq, Seq(_)))
            .filter { tx =>
              transactionsInfo.accounts(tx.accountId).institution.institution_id ==
                item.institution.institution_id
            },
          item.institution.name
        )
      }

    def downloadAllItem =
      Ant.Dropdown.Item("all")("All - no transfers") {
        download(transactionsInfo.transactions.flatMap(_.toOption), "all")
      }

    Ant.Dropdown(Ant.Dropdown.Trigger.Click)(
      Ant.Button()(Ant.Space()("Download", <.i(^.cls := "fa fa-angle-down")))
    )(
      items.map(downloadInstitutionItem) :+
        downloadAllItem
    )
  }
}
