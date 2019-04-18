package db.migration

import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context

class U20181102_01__Create_tables extends BaseJavaMigration {

  def migrate(context: Context): Unit = {
    val tables = Seq(
      "models"
    )

    val sqlCreateTables = tables.map(table ⇒ s"DROP TABLE ${table};")
    val statements = sqlCreateTables.map(context.getConnection.prepareStatement)

    try {
      statements.map(_.execute)
    } finally {
      statements.foreach(_.close())
    }
  }
}
