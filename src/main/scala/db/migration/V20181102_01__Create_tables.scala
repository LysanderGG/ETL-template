package db.migration

import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context

class V20181102_01__Create_tables extends BaseJavaMigration {

  def migrate(context: Context): Unit = {
    val tables = Seq(
      "models",
    )

    val sqlCreateTables = tables.map(table ⇒ s"""CREATE TABLE ${table}(
          id                 char(24) not null,
          created_at         timestamp not null,
          updated_at         timestamp not null,
          deleted_at         timestamp,
          data               jsonb,
          primary key (id, updated_at)
          )""")

    val statements = sqlCreateTables.map(context.getConnection.prepareStatement)

    try {
      statements.map(_.execute)
    } finally {
      statements.foreach(_.close())
    }
  }
}
