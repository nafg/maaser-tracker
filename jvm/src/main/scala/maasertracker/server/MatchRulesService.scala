package maasertracker.server

import maasertracker.generated.tables.SlickProfile.api.*
import maasertracker.generated.tables.Tables
import maasertracker.{Kind, Matchers, TransactionMatcher}

object MatchRulesService {
  case class Rules(rules: Seq[Tables.MatchRuleTable.Rec]) {
    val matchers =
      Matchers(
        rules.groupMap(row => Kind.withName(row.value.kind))(_.transform(TransactionMatcher.fromRow))
      )
  }

  def load = Tables.MatchRuleTable.Q.result.toIO.map(Rules(_))

  def delete(kind: String, matcher: TransactionMatcher.Lookup) =
    Tables.MatchRuleTable.Q
      .lookupQuery(matcher.transform(TransactionMatcher.toRow(kind, _)))
      .filter(_.kind === kind)
      .delete
}
