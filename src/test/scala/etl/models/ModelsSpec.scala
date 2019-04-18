package test.etl.models

import etl._
import etl.models._
import io.circe.DecodingFailure
import io.circe.parser.parse
import org.scalatest.WordSpec

class ModelsSpec extends WordSpec {
  "A ModelUpsertEvent" should {
    def parseModelUpsertEvent(json: String) = parse(json).right.get.as[ModelUpsertEvent].right.get
    "have the fields [id, createdAt, updatedAt]" in {
      val json =
        "{\"id\":\"5b763603c3bf3622d1001234\",\"created_at\":\"2017-02-02T17:54:00.000+09:00\",\"updated_at\":\"2017-02-03T19:12:34.000+09:00\"}"
      val ev: ModelUpsertEvent = parseModelUpsertEvent(json)
      assert(ev.id === "5b763603c3bf3622d1001234")
      assert(ev.createdAt.toString() === "2017-02-02T17:54+09:00")
      assert(ev.updatedAt.toString() === "2017-02-03T19:12:34+09:00")
    }
    "return DecodingFailure if a mandatory field is missing" in {
      val json = "{\"id\":\"5b763603c3bf3622d1001234\",\"created_at\":\"2017-02-02T17:54:00.000+09:00\"}"
      val err = parse(json).right.get.as[ModelUpsertEvent].left.get
      assert(err.isInstanceOf[DecodingFailure])
    }
    "have an optional deletedAt field" in {
      val json =
        "{\"id\":\"5b763603c3bf3622d1001234\",\"created_at\":\"2017-02-02T17:54:00.000+09:00\",\"updated_at\":\"2017-02-03T19:12:34.000+09:00\"}"
      val ev: ModelUpsertEvent = parseModelUpsertEvent(json)
      assert(ev.deletedAt === None)

      val json2 = "{\"id\":\"5b763603c3bf3622d1001234\",\"created_at\":\"2017-02-02T17:54:00.000+09:00\",\"updated_at\":\"2017-02-03T19:12:34.000+09:00\",\"deleted_at\":\"2018-01-01T10:00:00.000+09:00\"}"
      val ev2: ModelUpsertEvent = parseModelUpsertEvent(json2)
      assert(ev2.deletedAt.isDefined)
      assert(ev2.deletedAt.get.toString() === "2018-01-01T10:00+09:00")
    }
  }

  "A ModelDestroyEvent" should {
    def parseModelDestroyEvent(json: String) = parse(json).right.get.as[ModelDestroyEvent].right.get
    "have the fields [_id, deletedAt]" in {
      val json = "{\"_id\":\"5b763603c3bf3622d1001234\",\"deleted_at\":\"2017-02-02T17:54:00.000+09:00\"}"
      val mde = parseModelDestroyEvent(json)
      assert(mde._id === "5b763603c3bf3622d1001234")
      assert(mde.deletedAt.toString() === "2017-02-02T17:54+09:00")
    }
    "return DecodingFailure if a mandatory field is missing" in {
      val json = "{\"_id\":\"5b763603c3bf3622d1001234\"}"
      val err = parse(json).right.get.as[ModelDestroyEvent].left.get
      assert(err.isInstanceOf[DecodingFailure])

      val json2 = "{\"deleted_at\":\"2017-02-02T17:54:00.000+09:00\"}"
      val err2 = parse(json2).right.get.as[ModelDestroyEvent].left.get
      assert(err2.isInstanceOf[DecodingFailure])
    }
  }
}
