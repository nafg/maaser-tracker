package maasertracker.server

import maasertracker.TransactionMatcher
import maasertracker.generated.tables.Tables

object MatchRulesService {
  case class Rules(rules: Seq[Tables.MatchRuleTable.Rec]) {
    private lazy val grouped =
      rules.map(_.value).groupMap(_.kind) { row =>
        TransactionMatcher(
          institution = row.isInstitution,
          description = row.isDescription,
          id = row.isTransactionId,
          category = row.isCategory.map(_.linesIterator.toSeq)
        )
      }

    lazy val fulfillment = grouped.getOrElse("fulfillment", Nil)
    lazy val exemption   = grouped.getOrElse("exemption", Nil)
    lazy val transfer    = grouped.getOrElse("transfer", Nil)
  }

  def load = Tables.MatchRuleTable.Q.result.toIO.map(Rules)
}
