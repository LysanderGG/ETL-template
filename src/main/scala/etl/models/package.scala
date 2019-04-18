package etl

import io.circe.generic.extras._
import io.circe._
import io.circe.syntax._
import io.circe.generic.auto._
import io.circe.java8.time._
import java.time.ZonedDateTime

package object models {
  implicit val config: Configuration =
    Configuration.default.withSnakeCaseMemberNames

  sealed trait ModelEvent
  object ModelEvent

  @ConfiguredJsonCodec
  final case class ModelUpsertEvent(
      id: String,
      createdAt: ZonedDateTime,
      updatedAt: ZonedDateTime,
      deletedAt: Option[ZonedDateTime]
  ) extends ModelEvent

  @ConfiguredJsonCodec
  final case class ModelDestroyEvent(_id: String, deletedAt: ZonedDateTime) extends ModelEvent

  final case class KafkaMessage(event: String, version: Int, data: String)
  final case class ParseError(error: String, message: String)
}
