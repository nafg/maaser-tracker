import slick.additions.codegen.{EntityGenerationRules, snakeToCamel}
import slick.jdbc.meta.{MQName, MTable}

class MyGenerationRules(subpackage: String, override val container: String)(imports: String*)
    extends EntityGenerationRules {

  override def packageName = s"maasertracker.generated.$subpackage"

  override def extraImports = imports ++: super.extraImports

  override def includeTable(table: MTable) =
    table.name.schema.forall(_ == "public") && table.name.name != "flyway_schema_history"

  override def tableNameToIdentifier(name: MQName) = super.tableNameToIdentifier(name) + "Table"

  override def modelClassName(tableName: MQName) = snakeToCamel(tableName.name).capitalize + "Row"
}
