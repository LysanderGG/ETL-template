package etl.transformers

import java.time.ZonedDateTime
import scala.collection.immutable.Seq

sealed trait ModelAction {
  def modelName: String
  def id: String
}
object ModelAction

final case class ModelUpsert(
    modelName: String,
    id: String,
    createdAt: ZonedDateTime,
    updatedAt: ZonedDateTime,
    deletedAt: Option[ZonedDateTime],
    data: String
) extends ModelAction

object ModelUpsert {
  def apply(modelName: String, ev: etl.models.ModelUpsertEvent, data: String): ModelUpsert = {
    val cleanedData = data.replaceAll("\\x00", "")
    ModelUpsert(modelName, ev.id, ev.createdAt, ev.updatedAt, ev.deletedAt, cleanedData)
  }
}

final case class ModelDelete(modelName: String, id: String, deletedAt: ZonedDateTime) extends ModelAction

object ModelDelete {
  def apply(modelName: String, ev: etl.models.ModelDestroyEvent): ModelDelete = {
    ModelDelete(modelName, ev._id, ev.deletedAt)
  }
}
