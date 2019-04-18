package etl

import akka.stream._
import akka.stream.scaladsl._
import akka.{Done, NotUsed}
import akka.kafka.ConsumerMessage.{CommittableOffset, CommittableOffsetBatch}
import akka.kafka.scaladsl.Committer
import akka.stream.contrib.{AccumulateWhileUnchanged, PartitionWith}
import com.typesafe.config.{Config, ConfigFactory}
import etl.models._
import Kafka.KafkaCommittableMessage
import akka.dispatch.ExecutionContexts
import akka.kafka.CommitterSettings
import etl.loaders.{PostgresAction, PostgresModelDelete, PostgresModelUpsert}
import io.circe._
import io.circe.generic.auto._
import io.circe.parser._
import etl.transformers.{ModelAction, ModelDelete, ModelUpsert}
import io.sentry.Sentry
import io.sentry.event.Event
import io.sentry.event.EventBuilder

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scalikejdbc.config._
import scalikejdbc._

import scala.collection.immutable.Seq

class Stream(kafkaConfig: Config, killSwitch: SharedKillSwitch)(implicit ec: ExecutionContext, mat: ActorMaterializer) {
  DBs.setup('default)
  private val conf = ConfigFactory.load()
  val db: DB = DB(ConnectionPool.borrow())
  db.autoClose(false)

  private val useSentry = conf.getBoolean("sentry.use-sentry")
  private val maxActionsToAccumulate = conf.getInt("stream.max-actions-to-accumulate")
  private val maxDurationToAccumulate = conf.getInt("stream.max-duration-to-accumulate").milliseconds
  private val committerSettings = CommitterSettings(conf.getConfig("akka.kafka.committer"))

  // Log slow queries
  GlobalSettings.queryCompletionListener = (sql: String, params: scala.Seq[Any], millis: Long) ⇒ {
    if (millis > conf.getLong("postgres.log-queries-ms-threshold")) {
      println(s"""Slow pg query:
           |sql: $sql
           |params: ${params.mkString("[", ",", "]")}
           |duration: $millis ms""".stripMargin)
    }
  }

  private val eventsCounter: io.prometheus.client.Counter = io.prometheus.client.Counter
    .build()
    .name("akka_streams_metrics")
    .help("Akka stream metrics.")
    .labelNames("event")
    .register()

  private val bootstrapServers = s"${conf.getString("kafka.host")}:${conf.getString("kafka.port")}"
  private val source = Kafka.createSource(kafkaConfig, bootstrapServers, conf.getString("kafka.topics.models"))
  private val restartSource = RestartSource.withBackoff(
    minBackoff = 3.seconds,
    maxBackoff = 30.seconds,
    randomFactor = 0.2, // adds 20% "noise" to vary the intervals slightly
    maxRestarts = 20 // limits the amount of restarts to 20
  ) { () ⇒
    source
  }

  private val sinkDecider: Supervision.Decider = {
    case e: Exception ⇒
      eventsCounter.labels("exception").inc()
      if (useSentry) Sentry.capture(e) else System.err.println(e)
      Supervision.Restart
  }

  type ParseResult = Either[ParseError, (ModelAction, CommittableOffset)]

  /**
    * Ideally the transform should be done further down the pipeline in a dedicated Flow.
    * However, the json object is necessary to fill the 'data' field, and we don't want to carry it down the pipeline.
    * Therefore, the transform is done here.
    */
  private val jsonToModelAction: (String, Json) ⇒ Either[io.circe.Error, ModelAction] = {
    case (ev: String, json: Json) ⇒
      val extractedKeysUpsert = Seq("id", "created_at", "updated_at", "deleted_at")
      val patternUpserted = "(.*)(?:Created|Updated)".r
      val patternDestroyed = "(.*)Destroyed".r

      ev match {
        case patternUpserted(model) ⇒
          eventsCounter.labels("kafka_model_upserted").inc()
          val j = json.hcursor.downField(json.hcursor.keys.get.head) // skip the root level.
          for {
            jsonObj ← j.as[JsonObject]
            ev ← j.as[ModelUpsertEvent]
            data = Json.fromJsonObject(jsonObj.filterKeys(!extractedKeysUpsert.contains(_))).toString()
          } yield ModelUpsert(model, ev, data)
        case patternDestroyed(model) ⇒
          eventsCounter.labels("kafka_model_destroyed").inc()
          for (ev ← json.as[ModelDestroyEvent]) yield ModelDelete(model, ev)
      }
  }

  private val jsonParserFlow: Flow[KafkaCommittableMessage, ParseResult, NotUsed] =
    Flow[KafkaCommittableMessage]
      .map { msg ⇒
        eventsCounter.labels("kafka_all").inc()
        val r = for {
          parsedMessage ← parse(msg.record.value())
          kafkaMessage ← parsedMessage.as[KafkaMessage]
          parsedData ← parse(kafkaMessage.data)
          modelAction ← jsonToModelAction(kafkaMessage.event, parsedData)
        } yield (modelAction, msg.committableOffset)

        r.left.map(e ⇒ ParseError(e.getMessage, msg.record.value()))
      }
      .log("json parser")

  private val splitModelActions: ((ModelAction, CommittableOffset)) ⇒ String =
    _._1 match {
      case m: ModelUpsert ⇒ m.modelName + "Upsert"
      case m: ModelDelete ⇒ m.modelName + "Delete"
    }

  val accumulateByEventType = new AccumulateWhileUnchanged[(ModelAction, CommittableOffset), String](
    splitModelActions,
    Some(maxActionsToAccumulate),
    Some(maxDurationToAccumulate))

  // All ModelAction are of the same model.
  private def runPostgresAction[A <: ModelAction](pgAction: PostgresAction[A], actionStr: String): Seq[A] ⇒ Seq[Int] =
    models ⇒ {
      eventsCounter.labels(s"postgres_${actionStr}_attempt").inc(models.length)
      eventsCounter.labels(s"postgres_${actionStr}_attempt_batches").inc(1)

      val valuesUpdate = models.map(pgAction.seq)
      val table = models.head.modelName
      val res = db.autoCommit { implicit session ⇒
        pgAction.sql(table).batch(valuesUpdate: _*).apply()
      }

      eventsCounter.labels(s"postgres_${actionStr}_success").inc(models.length)
      eventsCounter.labels(s"postgres_${actionStr}_success_batches").inc(1)
      res
    }

  // All ModelAction are of the same model.
  private val sinkModelActions: Sink[Seq[(ModelAction, CommittableOffset)], Future[Done]] =
    Flow[Seq[(ModelAction, CommittableOffset)]]
      .map { seq ⇒
        val (actions, offsets) = seq.unzip
        actions.head match {
          case _: ModelUpsert ⇒ runPostgresAction(PostgresModelUpsert, "upsert")(actions.asInstanceOf[Seq[ModelUpsert]])
          case _: ModelDelete ⇒ runPostgresAction(PostgresModelDelete, "delete")(actions.asInstanceOf[Seq[ModelDelete]])
        }
        offsets
      }
      .log("postgres model actions")
      .mapConcat(identity)
      .toMat(Committer.sink(committerSettings))(Keep.right) // We ignore the return value of the flow, and keep the materialized value
      .withAttributes(ActorAttributes.supervisionStrategy(sinkDecider))

  private val processParseError: ParseError ⇒ Unit = parseError ⇒ {
    eventsCounter.labels("parse_error").inc()
    if (useSentry) {
      val eventBuilder = new EventBuilder()
        .withMessage(parseError.toString)
        .withLevel(Event.Level.ERROR)
        .withLogger(ParseError.getClass.getName)

      Sentry.capture(eventBuilder)
    } else {
      Console.err.println(parseError)
    }
  }

  private val sinkParseErrors: Sink[ParseError, Future[Done]] =
    Flow[ParseError]
      .map(processParseError)
      .log("log to sentry")
      .toMat(Sink.ignore)(Keep.right)
      .withAttributes(ActorAttributes.supervisionStrategy(sinkDecider))

  val graph: RunnableGraph[Future[Done]] =
    RunnableGraph.fromGraph(GraphDSL.create(sinkModelActions) { implicit b ⇒ sinkModelActions ⇒
      import GraphDSL.Implicits._

      val splitParseResult = b.add(PartitionWith[ParseResult, ParseError, (ModelAction, CommittableOffset)](identity))

      restartSource.via(killSwitch.flow) ~> jsonParserFlow ~> splitParseResult.in
      splitParseResult.out0 ~> sinkParseErrors
      splitParseResult.out1 ~> accumulateByEventType ~> sinkModelActions

      ClosedShape
    })
}
