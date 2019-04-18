package etl

import akka.stream.scaladsl.Source
import com.typesafe.config.Config

object Kafka {

  import akka.kafka.ConsumerMessage.CommittableMessage
  import akka.kafka.{ConsumerSettings, Subscriptions}
  import akka.kafka.scaladsl.Consumer
  import org.apache.kafka.clients.consumer.ConsumerConfig
  import org.apache.kafka.common.serialization.{ByteArrayDeserializer, StringDeserializer}

  private def consumerSettings(config: Config, bootstrapServers: String) =
    ConsumerSettings(config, new ByteArrayDeserializer, new StringDeserializer)
      .withBootstrapServers(bootstrapServers)
      .withGroupId("general_etl")
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")

  type KafkaCommittableMessage = CommittableMessage[Array[Byte], String]

  def createSource(config: Config,
                   bootstrapServers: String,
                   topics: String*): Source[KafkaCommittableMessage, Consumer.Control] =
    Consumer.committableSource(consumerSettings(config, bootstrapServers), Subscriptions.topics(topics.toSet))
}
