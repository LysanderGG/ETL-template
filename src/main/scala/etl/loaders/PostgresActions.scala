package etl.loaders

import etl.transformers.{ModelDelete, ModelUpsert}
import scalikejdbc._

import scala.collection.immutable.Seq

trait PostgresAction[T] {
  def sql(table: String): SQL[Nothing, NoExtractor]
  def seq(t: T): Seq[Any]

  // Simple enough not to use a library. Used to convert CamelCase to snake_case for Postgres table names.
  def snakify(name: String) =
    name.replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2").replaceAll("([a-z\\d])([A-Z])", "$1_$2").toLowerCase

  /**
   * Using a library looks like overkill for this too.
   * Used for converting singular model names to plural for table names.
   * Does not cover everything but is good enough for the words we use.
   * s must be a lower case string.
   */
  def plurialize(s: String) =
    s.takeRight(2) match {
      case "sh" | "ch" ⇒ s + "es"
      case "ay"        ⇒ s + "s"
      case _ ⇒
        s.takeRight(1) match {
          case "s" ⇒ s + "es"
          case "y" ⇒ s.dropRight(1) + "ies"
          case _   ⇒ s + "s"
        }
    }
}

object PostgresModelUpsert extends PostgresAction[ModelUpsert] {
  override def sql(table: String): SQL[Nothing, NoExtractor] = {
    val table_sql = SQLSyntax.createUnsafely(plurialize(snakify(table)))
    sql"""
        INSERT INTO $table_sql (id, created_at, updated_at, deleted_at, data)
        VALUES (?, ?, ?, ?, ?::json)
        ON CONFLICT (id, updated_at) DO UPDATE SET (created_at, data)
        = (EXCLUDED.created_at, EXCLUDED.data)
      """
  }

  override def seq(m: ModelUpsert): Seq[Any] = {
    Seq(m.id, m.createdAt, m.updatedAt, m.deletedAt, m.data)
  }
}

object PostgresModelDelete extends PostgresAction[ModelDelete] {
  override def sql(table: String): SQL[Nothing, NoExtractor] = {
    val table_sql = SQLSyntax.createUnsafely(plurialize(snakify(table)))
    sql"UPDATE $table_sql SET deleted_at = ? WHERE id = ? AND deleted_at IS NULL;"
  }

  override def seq(m: ModelDelete): Seq[Any] = {
    Seq(m.deletedAt, m.id)
  }
}
