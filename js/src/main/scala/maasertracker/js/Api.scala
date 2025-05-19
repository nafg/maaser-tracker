package maasertracker.js

import maasertracker.{AddItemRequest, Institution, PlaidItem, TransactionsInfo}

object Api {
  def getPlaidLinkToken                               = Ajax.get[String]("/api/plaid-link-token")
  def getItems                                        = Ajax.get[Seq[PlaidItem]]("/api/items")
  def getTransactions                                 = Ajax.get[TransactionsInfo]("/api/transactions")
  def getLinkToken(item: PlaidItem)                   = Ajax.get[String](s"/api/linkToken/${item.itemId}")
  def addItem(publicToken: String, inst: Institution) = Ajax.post[Unit]("/api/items", AddItemRequest(publicToken, inst))
  def deleteItem(item: PlaidItem)                     = Ajax.delete(s"/api/items/${item.itemId}")
}
