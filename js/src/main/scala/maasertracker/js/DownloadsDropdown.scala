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
import maasertracker.js.facades.ant

object DownloadsDropdown {
  private class TransactionHeaderEncoder(accounts: AccountInfos, tags: Map[String, Tags.Value])
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
      val accountInfo = accounts.byId(d.accountId)
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
        tags.get(d.transactionId).fold("")(_.toString)
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
    implicit val headerEncoder =
      new TransactionHeaderEncoder(transactionsInfo.transactions.accounts, transactionsInfo.tags)

    def downloadInstitutionItem(item: PlaidItem) =
      ant.Dropdown.Item(item.institution.institution_id)(item.institution.name) {
        download(
          transactionsInfo.transactions.items
            .flatMap(_.fold(_.toSeq, Seq(_)))
            .filter { tx =>
              transactionsInfo.transactions.accounts.byId(tx.accountId).institution.institution_id ==
                item.institution.institution_id
            },
          item.institution.name
        )
      }

    def downloadAllItem =
      ant.Dropdown.Item("all")("All - no transfers") {
        download(transactionsInfo.transactions.items.flatMap(_.toOption), "all")
      }

    ant.Dropdown.click(
      ant.Button()(ant.Space()("Download", <.i(^.cls := "fa fa-angle-down")))
    )(
      items.map(downloadInstitutionItem) :+
        downloadAllItem
    )
  }
}
