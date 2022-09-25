package maasertracker.server

import maasertracker.TransactionMatcher
import maasertracker.generated.tables.Tables

object MatchRulesService {
  case class Rules(rules: Seq[Tables.MatchRuleTable.Rec]) {
    private lazy val grouped =
      rules.map(_.value).groupMap(_.kind) { row =>
        TransactionMatcher(
          id = row.isTransactionId,
          institution = row.isInstitution,
          description = row.isDescription,
          category = row.isCategory.map(_.linesIterator.toSeq),
          minAmount = row.minAmount,
          maxAmount = row.maxAmount
        )
      }

    lazy val transfer = grouped.getOrElse("transfer", Nil)
    lazy val income = grouped.getOrElse("income", Nil)
    lazy val exemption   = grouped.getOrElse("exemption", Nil)
    lazy val fulfillment = grouped.getOrElse("fulfillment", Nil)
  }

  def load = Tables.MatchRuleTable.Q.result.toIO.map(Rules)
}
