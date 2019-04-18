package etl

import scala.concurrent.ExecutionContext.Implicits.global
import akka.http.scaladsl.server.HttpApp
import com.typesafe.config.{Config, ConfigFactory}

object WebServer extends HttpApp {
  override def routes = PrometheusService.route

  private val conf = ConfigFactory.load()
  private val host = conf.getString("prometheus.webserver.host")
  private val port = conf.getInt("prometheus.webserver.port")
  def start(): Unit = startServer(host, port)
}
