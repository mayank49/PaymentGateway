package prac.fin.api.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import prac.fin.payment.common.dto.event.PaymentEventMessage;

@Configuration
public class KafkaProducerConfig {

	@Value("${spring.kafka.bootstrap-servers}")
	private String bootstrapServers;
	
	@Bean
	public ProducerFactory<String, PaymentEventMessage> producerFactory() {
		Map<String, Object> config = new HashMap<>();
		config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
		config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
		config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
		// All in-sync replicas must acknowledge. No message loss
		config.put(ProducerConfig.ACKS_CONFIG, "all");
		// Retry on transient broker errors
		config.put(ProducerConfig.RETRIES_CONFIG, 3);
		// Prevents duplicate messages on retry
		// Kafka Broker attaches the producer id to each producer
		// Every message from producer has a increasing sequence number 0, 1, 2...
		// If a message from same producer with same sequence number
		// comes again, Kafka discards the duplicate message but still
		// sends "success" acknowledgement back to producer.
		// If the sequence number is much higher than what was last seen by broker,
		// then broker knows that messages were lost and throws an error.
		// ACKS_CONFIG must be all for broker to promise that messages won't be duplicated.
		// If the producer crashes, it gets new producer id.
		config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
		return new DefaultKafkaProducerFactory<>(config);
	}
	
	@Bean
    public KafkaTemplate<String, PaymentEventMessage> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
