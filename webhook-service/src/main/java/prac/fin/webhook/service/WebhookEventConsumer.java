package prac.fin.webhook.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import prac.fin.payment.common.dto.event.PaymentEventMessage;
import prac.fin.payment.domain.entity.Merchant;
import prac.fin.payment.domain.entity.PaymentIntent;
import prac.fin.payment.domain.entity.WebhookEvent;
import prac.fin.payment.domain.repository.MerchantRepository;
import prac.fin.payment.domain.repository.PaymentIntentRepository;
import prac.fin.payment.domain.repository.WebhookEventRepository;
import prac.fin.webhook.service.dto.WebhookDeliveryStatus;

/**
 * Consumes payment outcome events from Kafka and delivers webhooks to merchants.
 * If the webhook HTTP POST fails, writes the webhook event row to DB.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookEventConsumer {
	
	private final ObjectMapper objectMapper;
	private final WebhookEventRepository webhookEventRepository;
	private final MerchantRepository merchantRepository;
    private final PaymentIntentRepository intentRepository;
    private final WebhookDeliveryService deliveryService;

	@KafkaListener(topics = {
		PaymentEventMessage.TOPIC_SUCCEEDED,
		PaymentEventMessage.TOPIC_FAILED,
		PaymentEventMessage.TOPIC_UNKNOWN,
		PaymentEventMessage.TOPIC_ACTION_REQUIRED
	})
	public void onPaymentEvent(@Payload PaymentEventMessage event,
			Acknowledgment acknowledgment) {
		log.info("Received payment event: type={} intentId={}",
                event.getEventType(), event.getIntentId());
		
		String webhookUrl = event.getMerchantWebhookUrl();
		if(webhookUrl == null || webhookUrl.isBlank()) {
			log.info("Merchant {} has no webhook URL. Skipping message.", event.getMerchantId());
			acknowledgment.acknowledge();
            return;
		}
		
		String payloadJson = buildPayload(event);
		
		WebhookDeliveryStatus status = deliveryService.deliver(webhookUrl, payloadJson, 
				event.getMerchantId(), event.getEventType(), 1);
		
		if(status.isDelivered()) {
			log.info("Webhook delivered immediately: type={} intentId={}",
                    event.getEventType(), event.getIntentId());
		} else {
			writeRetryRow(event, payloadJson);
            log.warn("Webhook delivery failed. Retry row written: type={} intentId={}",
                    event.getEventType(), event.getIntentId());
		}
		
		// Commit offset only after we've either delivered or persisted the retry row
		acknowledgment.acknowledge();
	}
	
	private String buildPayload(PaymentEventMessage event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            log.error("Failed to serialize webhook payload for intentId={}", event.getIntentId(), e);
            return "{}";
        }
    }
	
	private void writeRetryRow(PaymentEventMessage event, String payloadJson) {
		try {
			Optional<Merchant> merchant = merchantRepository.findById(
                    UUID.fromString(event.getMerchantId()));
            String intentIdRaw = event.getIntentId().substring("pi_".length());
            Optional<PaymentIntent> intent = intentRepository.findById(UUID.fromString(intentIdRaw));
 
            if (merchant.isEmpty() || intent.isEmpty()) {
                log.error("Cannot write retry row — merchant or intent not found: {}",
                        event.getIntentId());
                return;
            }
 
            WebhookEvent webhookEvent = WebhookEvent.builder()
                    .merchant(merchant.get())
                    .paymentIntent(intent.get())
                    .eventType(event.getEventType())
                    .payload(objectMapper.convertValue(event, 
                            new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {}))
                    .attemptCount(1)
                    .nextAttemptAt(Instant.now().plus(Duration.ofSeconds(10)))
                    .build();
 
            webhookEventRepository.save(webhookEvent);
 
        } catch (Exception e) {
            log.error("CRITICAL: Failed to write webhook retry row for intentId={}: {}",
                    event.getIntentId(), e.getMessage(), e);
        }
	}
}
