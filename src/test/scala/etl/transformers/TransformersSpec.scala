package test.etl.transformers

import java.time.ZonedDateTime

import etl._
import etl.models.{ModelDestroyEvent, ModelUpsertEvent}
import etl.transformers.{ModelUpsert, ModelDelete}
import org.scalatest.prop._
import org.scalatest.WordSpec

class TransformersSpec extends WordSpec {
  "A ModelUpsert" should {
    "copy the fields from ModelUpsertEvent" in {
      val ev: ModelUpsertEvent = new ModelUpsertEvent("id", ZonedDateTime.parse("2017-02-02T17:54+09:00"), ZonedDateTime.parse("2017-03-03T23:33+09:00"), None)
      val upsert = ModelUpsert("model", ev, "data")
      assert(upsert.modelName === "model")
      assert(upsert.id === ev.id)
      assert(upsert.createdAt === ev.createdAt)
      assert(upsert.updatedAt === ev.updatedAt)
      assert(upsert.deletedAt === ev.deletedAt)
      assert(upsert.data === "data")
    }

    "filter out \\x00 characters" in {
      val x00 = Character.toString(0)
      val badData = "{memo : " + x00 + "ゆ" + x00 + x00 +"うき}"
      val ev: ModelUpsertEvent = new ModelUpsertEvent("id", ZonedDateTime.parse("2017-02-02T17:54+09:00"), ZonedDateTime.parse("2017-03-03T23:33+09:00"), None)
      val upsert = ModelUpsert("model", ev, badData)
      assert(upsert.modelName === "model")
      assert(upsert.id === ev.id)
      assert(upsert.createdAt === ev.createdAt)
      assert(upsert.updatedAt === ev.updatedAt)
      assert(upsert.deletedAt === ev.deletedAt)
      assert(upsert.data === "{memo : ゆうき}")
    }
  }

  "A ModelDelete" should {
    "copy the fields from ModelDestroyEvent" in {
      val ev: ModelDestroyEvent = new ModelDestroyEvent("id", ZonedDateTime.parse("2017-02-02T17:54+09:00"))
      val delete = ModelDelete("model", ev)
      assert(delete.modelName === "model")
      assert(delete.id === ev._id)
      assert(delete.deletedAt === ev.deletedAt)
    }
  }
}
