package prac.fin.webhook.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import prac.fin.payment.common.dto.event.PaymentEventMessage;


@Configuration
public class KafkaConsumerConfig {

	@Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;
	
	@Bean
	public ConsumerFactory<String, PaymentEventMessage> consumerFactory() {
		
		JsonDeserializer<PaymentEventMessage> deserializer = new JsonDeserializer<>(PaymentEventMessage.class);
		/*
		 * By-default, JsonDeserializer may try to read type information from Kafka
		 * message headers. If the producer didn't send these headers or send different
		 * class names, deserialization will fail.
		 */
		 deserializer.setRemoveTypeHeaders(false);
		 
		 /*
		  * In Spring Kafka, the JsonDeserializer includes security features that prevent
		  * the deserialization of classes unless they are explicitly added to a 
		  * trusted packages.
		  */
		 deserializer.addTrustedPackages("prac.fin.payment.common.dto.event");
		 
		Map<String, Object> config = new HashMap<>();
		config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
		config.put(ConsumerConfig.GROUP_ID_CONFIG, "webhook-service");
		
		/*
		 * This determines where the consumer starts reading if there 
		 * is no previous "bookmark" (offset) for its consumer group.
		 */
		config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
		
		/*
		 * Disables the background thread that periodically "commits" (saves) 
		 * the last processed message offset to Kafka.
		 * 
		 * It gives you manual control over when a message is considered processed.
		 * 
		 * This is critical for ensuring "at-least-once" delivery. You only commit 
		 * the offset after your code has successfully finished processing the message
		 */
		config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
		
		return new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), deserializer);
	}
	
	/**
	 * A KafkaListenerContainerFacotry creates one or more KafkaListenerContainer instances.
	 * Each container is a background thread that polls Kafka for messages.
	 * 
	 * It connects the ConsumerFactory to the listener.
	 * 
	 * Spring Boot auto-configuration created a default factory for you. 
	 * When Spring Boot sees that you have defined a bean named 
	 * kafkaListenerContainerFactory, it "backs off" and stops creating the default one.
	 * 
	 * When Spring sees @KafkaListerner it uses the factory to build a worker to listen
	 * to messages on a topic. The worker will then reflectively call your method with the
	 * deserialized object as the argument.
	 * 
	 * ConcurrentKafkaListenerContainerFactory can create multiple threads based on
	 * the concurrency configuration.
	 * 
	 * @return
	 */
	@Bean
	public ConcurrentKafkaListenerContainerFactory<String, PaymentEventMessage> kafkaListenerContainerFactory() {
		ConcurrentKafkaListenerContainerFactory<String, PaymentEventMessage> factory =
				new ConcurrentKafkaListenerContainerFactory<>();
		factory.setConsumerFactory(consumerFactory());
		 // Manual offset commit. Only ack after webhook delivered or retry row written
		factory.getContainerProperties().setAckMode(
                org.springframework.kafka.listener.ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
	}
}
