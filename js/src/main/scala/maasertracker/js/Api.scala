package maasertracker.js

import maasertracker.*

object Api {
  object LinkTokens {
    def get                  = Ajax.get[String]("/api/plaid-link-token")
    def get(item: PlaidItem) = Ajax.get[String](s"/api/linkToken/${item.itemId}")
  }

  object Items {
    def get                                         = Ajax.get[Seq[PlaidItem]]("/api/items")
    def add(publicToken: String, inst: Institution) = Ajax.post[Unit]("/api/items", AddItemRequest(publicToken, inst))
    def delete(item: PlaidItem)                     = Ajax.delete(s"/api/items/${item.itemId}")
  }

  object Transactions {
    def get = Ajax.get[PlaidData]("/api/transactions")
  }

  object MatchRules {
    def get                                             = Ajax.get[Matchers]("/api/match-rules")
    def add(kind: Kind, matcher: TransactionMatcher)    = Ajax.post[Unit](s"/api/match-rules/${kind.name}/add", matcher)
    def delete(kind: Kind, matcher: TransactionMatcher) =
      Ajax.post[Unit](s"/api/match-rules/${kind.name}/delete", matcher)
  }
}
