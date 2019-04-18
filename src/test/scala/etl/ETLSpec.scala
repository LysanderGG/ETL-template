package test.etl.integration

import java.time.{ZoneId, ZonedDateTime}

import etl._
import org.scalatest.{BeforeAndAfter, Suite, WordSpec}
import scalikejdbc.config._
import scalikejdbc._

import scala.concurrent.ExecutionContext
import akka.actor.{ActorRef, ActorSystem}
import com.typesafe.config.ConfigFactory
import io.prometheus.client.CollectorRegistry
import net.manub.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}
import org.flywaydb.core.Flyway

trait PostgresSpec extends Suite with BeforeAndAfter {
  private val conf = ConfigFactory.load()
  private val flyway = Flyway.configure
    .dataSource(conf.getString("db.default.url"),
                conf.getString("db.default.user"),
                conf.getString("db.default.password"))
    .load

  before {
    flyway.migrate
  }

  after {
    flyway.clean()

    // https://github.com/openzipkin/zipkin/pull/1812
    CollectorRegistry.defaultRegistry.clear()
  }
}

class ETLSpec extends WordSpec with EmbeddedKafka with PostgresSpec {
  "ETL" should {
    val userDefinedConfig = EmbeddedKafkaConfig(kafkaPort = 0, zooKeeperPort = 0)
    val topic = "tst.ts.data.models"

    System.setProperty("KAFKA_TOPIC_PREFIX", "tst")
    def sqlSelect(table: String) = SQL(s"SELECT * FROM $table;")
    var etlActor: ActorRef = null

    def startEtl(): Unit = {
      val system: ActorSystem = ActorSystem("generalETL")
      implicit val ec: ExecutionContext = system.dispatcher
      etlActor = system.actorOf(ETL.props, "etl")
      etlActor ! ETL.Start
    }

    def stopETL(): Unit = {
      etlActor ! ETL.Stop
    }

    case class DBModel(id: String,
                       createdAt: ZonedDateTime,
                       updatedAt: ZonedDateTime,
                       deletedAt: Option[ZonedDateTime],
                       data: String)
    def selectAll(table: String): List[DBModel] = {
      DB.readOnly { implicit session ⇒
        sqlSelect(table)
          .map { rs ⇒
            DBModel(
              id = rs.string("id"),
              createdAt = rs.get("created_at"),
              updatedAt = rs.get("updated_at"),
              deletedAt = rs.get("deleted_at"),
              data = rs.string("data"),
            )
          }
          .list
          .apply()
      }
    }

    def selectAllIds(table: String): List[String] = {
      DB.readOnly { implicit session ⇒
        sqlSelect(table).map(_.string("id")).list.apply()
      }
    }

    "insert a row in Postgres for ModelCreated messages" in {
      withRunningKafkaOnFoundPort(userDefinedConfig) { implicit actualConfig ⇒
        System.setProperty("KAFKA_PORT", actualConfig.kafkaPort.toString)

        ConfigFactory.invalidateCaches()
        startEtl()

        Thread.sleep(20000)

        assert(selectAllIds("models").isEmpty)

        val message =
          "{\"event\":\"ModelCreated\",\"version\":1,\"timestamp\":\"2017-02-02T08:54:00.000Z\",\"data\":\"{...}\"}"
        publishStringMessageToKafka(topic, message)

        Thread.sleep(10000)

        val res = selectAll("models")
        assert(res.size == 1)
        val s = res.head
        assert(s.id === "5b763603c3bf3622d1000004")
        assert(s.createdAt.withZoneSameInstant(ZoneId.of("UTC")).toString === "2017-02-02T08:54Z[UTC]")
        assert(s.updatedAt.withZoneSameInstant(ZoneId.of("UTC")).toString === "2017-02-02T08:54Z[UTC]")
        assert(s.deletedAt.isEmpty)
        assert(s.data === "{...}")

        stopETL()
      }
    }

    "upsert for ModelUpdated messages" in {
      withRunningKafkaOnFoundPort(userDefinedConfig) { implicit actualConfig ⇒
        System.setProperty("KAFKA_PORT", actualConfig.kafkaPort.toString)

        ConfigFactory.invalidateCaches()
        startEtl()

        Thread.sleep(20000)

        assert(selectAllIds("models").isEmpty)

        var message =
          "{\"event\":\"ModelUpdated\",\"version\":1,\"timestamp\":\"2017-02-02T08:54:00.000Z\",\"data\":\"{...}\"}"
        publishStringMessageToKafka(topic, message)

        Thread.sleep(10000)

        var res = selectAll("models")
        assert(res.size == 1)
        var s = res.head
        assert(s.id === "5b763603c3bf3622d1000004")
        assert(s.createdAt.withZoneSameInstant(ZoneId.of("UTC")).toString === "2017-02-02T08:54Z[UTC]")
        assert(s.updatedAt.withZoneSameInstant(ZoneId.of("UTC")).toString === "2017-02-02T08:54Z[UTC]")
        assert(s.deletedAt.isEmpty)
        assert(s.data === "{...}")

        // Remove emails
        // change other_field
        message =
          "{\"event\":\"ModelUpdated\",\"version\":1,\"timestamp\":\"2017-02-02T08:54:00.000Z\",\"data\":\"{...}\"}"
        publishStringMessageToKafka(topic, message)

        Thread.sleep(3000)

        res = selectAll("models")
        assert(res.size == 1)
        s = res.head
        assert(s.id === "5b763603c3bf3622d1000004")
        assert(s.createdAt.withZoneSameInstant(ZoneId.of("UTC")).toString === "2017-02-02T08:54Z[UTC]")
        assert(s.updatedAt.withZoneSameInstant(ZoneId.of("UTC")).toString === "2017-02-02T08:54Z[UTC]")
        assert(s.deletedAt.isEmpty)
        assert(s.data === "{...}")

        stopETL()
      }
    }

    "set deletedAt field for ModelDeleted messages" in {
      withRunningKafkaOnFoundPort(userDefinedConfig) { implicit actualConfig ⇒
        System.setProperty("KAFKA_PORT", actualConfig.kafkaPort.toString)

        ConfigFactory.invalidateCaches()
        startEtl()

        Thread.sleep(20000)

        assert(selectAllIds("models").isEmpty)

        var message =
          "{\"event\":\"ModelCreated\",\"version\":1,\"timestamp\":\"2017-02-02T08:54:00.000Z\",\"data\":\"{...}\"}"
        publishStringMessageToKafka(topic, message)

        Thread.sleep(10000)

        var res = selectAll("models")
        assert(res.size == 1)
        assert(res.head.id === "5b763603c3bf3622d1000004")
        assert(res.head.deletedAt.isEmpty)

        message =
          "{\"event\":\"ModelDestroyed\",\"version\":1,\"timestamp\":\"2017-02-02T08:54:00.000Z\",\"data\":\"{...}\"}"
        publishStringMessageToKafka(topic, message)

        Thread.sleep(3000)

        res = selectAll("models")
        assert(res.size == 1)
        assert(res.head.id === "5b763603c3bf3622d1000004")
        assert(res.head.deletedAt.isDefined)
        assert(res.head.deletedAt.get.withZoneSameInstant(ZoneId.of("UTC")).toString === "2017-02-02T08:54Z[UTC]")

        stopETL()
      }
    }
  }
}
