package etl

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.stream.{ActorMaterializer, KillSwitches}

import scala.concurrent.ExecutionContext
import scala.concurrent._

object ETL {
  def props: Props = Props(new ETL())

  case object Start
  case object Stop
}

final class ETL extends Actor with ActorLogging {
  implicit private val system: ActorSystem = context.system
  implicit private val mat: ActorMaterializer = ActorMaterializer()
  implicit private val ec: ExecutionContext = context.system.dispatcher

  private val ks = KillSwitches.shared("etlKillSwitch")
  private val stream = new Stream(
    kafkaConfig = context.system.settings.config.getConfig("akka.kafka.consumer"),
    killSwitch = ks
  )
  private val ctx = context // In order to use context from another thread.

  override def receive: Receive = {
    case ETL.Start ⇒
      println("Starting...")
      stream.graph.run().onComplete { _ ⇒
        ctx.stop(self)
        ctx.system.terminate()
      }
      println("Started")
    case ETL.Stop ⇒
      println("Stopping...")
      ks.shutdown()
      println("Stopped")
  }
}
