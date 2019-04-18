package etl

import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigFactory}
import org.flywaydb.core.Flyway
import scala.concurrent.ExecutionContext
import io.sentry.Sentry

object Application extends App {
  def flywayMigrate(): Unit = {
    val conf = ConfigFactory.load()
    val flyway = Flyway.configure
      .dataSource(conf.getString("db.default.url"),
                  conf.getString("db.default.user"),
                  conf.getString("db.default.password"))
      .load

    flyway.migrate
  }

  Sentry.init()

  flywayMigrate()

  val system: ActorSystem = ActorSystem("generalETL")
  implicit val ec: ExecutionContext = system.dispatcher

  val etlActor = system.actorOf(ETL.props, "etl")
  etlActor ! ETL.Start

  WebServer.start()
}
