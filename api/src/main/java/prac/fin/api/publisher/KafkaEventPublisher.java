package prac.fin.api.publisher;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import prac.fin.payment.common.dto.event.PaymentEventMessage;
import prac.fin.payment.domain.entity.PaymentIntent;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaEventPublisher {

	private final KafkaTemplate<String, PaymentEventMessage> kafkaTemplate;
	
	public void publishSucceded(PaymentIntent intent, String processorPaymentId) {
		PaymentEventMessage message = buildMessage(intent, processorPaymentId, null, PaymentEventMessage.TOPIC_SUCCEEDED);
		publish(intent, PaymentEventMessage.TOPIC_SUCCEEDED, message);
	}
	
	public void publishFailed(PaymentIntent intent) {
		PaymentEventMessage message = buildMessage(intent, null, null, PaymentEventMessage.TOPIC_FAILED);
		publish(intent, PaymentEventMessage.TOPIC_FAILED, message);
	}
	
	public void publishUnknown(PaymentIntent intent) {
		PaymentEventMessage message = buildMessage(intent, null, null, PaymentEventMessage.TOPIC_UNKNOWN);
		publish(intent, PaymentEventMessage.TOPIC_UNKNOWN, message);
	}
	
	public void publishActionRequired(PaymentIntent intent, String redirectUrl) {
		PaymentEventMessage message = buildMessage(intent, null, redirectUrl, PaymentEventMessage.TOPIC_ACTION_REQUIRED);
		publish(intent, PaymentEventMessage.TOPIC_ACTION_REQUIRED, message);
	}
	
	private PaymentEventMessage buildMessage(PaymentIntent intent, String processorPaymentId, String redirectUrl, String topic) {
		return PaymentEventMessage.builder()
                .eventType(topic)
                .intentId("pi_" + intent.getId())
                .merchantId(intent.getMerchant().getId().toString())
                .amount(intent.getAmount())
                .currency(intent.getCurrency())
                .status(intent.getStatus())
                .paymentMethodType(intent.getPaymentMethodType())
                .processorPaymentId(processorPaymentId)
                .redirectUrl(redirectUrl)
                .metadata(intent.getMetadata())
                .merchantWebhookUrl(intent.getMerchant().getWebhookUrl())
                .occurredAt(Instant.now())
                .build();
	}
	
	/**
	 * Publishes payment outcome events to Kafka.	
	 * 
	 * All events for the same intent land on the same Kafka partition,
	 * guaranteeing they are consumed in order by downstream consumers.
	 * 
	 * Failure and Success are logged.
	 * 
	 * @param intent
	 * @param topic
	 * @param message
	 */
	private void publish(PaymentIntent intent, String topic, PaymentEventMessage message) {
		// intentId is the message key
		// guarantees partition ordering per intent
        String key = intent.getId().toString();
        
        CompletableFuture<SendResult<String, PaymentEventMessage>> future 
        		= kafkaTemplate.send(topic, key, message);
        
        /*
         * Kafka publish is async. We attach a callback to log failures.
		 * We do not throw error if Kafka is unavailable because the
		 * payment has already succeeded and we don't want to return an error
		 * to the customer.
		 * 
		 * This is acceptable because,
		 *  - Kafka downtime are rare and brief
		 *  - The merchant can always poll GET /payment-intent/{id}
		 *  - Our fallback will send "pending events" to merchant eventually  
         */
        future.whenComplete((result, ex) -> {
        	if(ex != null) {
        		log.error("Failed to publish {} event for intentId={}: {}",
                        topic, key, ex.getMessage());
        	} else {
        		log.debug("Published {} event: intentId={} partition={} offset={}",
                        topic, key,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
        	}
        });
	}
}
