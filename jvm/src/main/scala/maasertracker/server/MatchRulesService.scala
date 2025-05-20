package maasertracker.server

import maasertracker.generated.models.MatchRuleRow
import maasertracker.generated.tables.SlickProfile.api.*
import maasertracker.generated.tables.Tables
import maasertracker.{Kind, Matchers, TransactionMatcher}

object MatchRulesService {
  case class Rules(rules: Seq[Tables.MatchRuleTable.Rec]) {
    private val grouped = rules.map(_.value).groupMap(row => Kind.withName(row.kind))(TransactionMatcher.fromRow)

    val matchers =
      Matchers(
        transfer = grouped.getOrElse(Some(Kind.Transfer), Nil),
        income = grouped.getOrElse(Some(Kind.Income), Nil),
        nonMaaserIncome = grouped.getOrElse(Some(Kind.Exemption), Nil),
        maaserPayment = grouped.getOrElse(Some(Kind.Fulfillment), Nil)
      )
  }

  def load = Tables.MatchRuleTable.Q.result.toIO.map(Rules(_))

  def findRow(row: MatchRuleRow) =
    Tables.MatchRuleTable.Q
      .filter { r =>
        r.kind === row.kind &&
        (r.isTransactionId.isEmpty || r.isTransactionId === row.isTransactionId) &&
        (r.isDescription.isEmpty || r.isDescription === row.isDescription) &&
        (r.isInstitution.isEmpty || r.isInstitution === row.isInstitution) &&
        (r.isCategory.isEmpty || r.isCategory === row.isCategory) &&
        (r.minAmount.isEmpty || r.minAmount === row.minAmount) &&
        (r.maxAmount.isEmpty || r.maxAmount === row.maxAmount)
      }
}
