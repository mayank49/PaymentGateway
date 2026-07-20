package prac.fin.webhook.service;

import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import prac.fin.payment.domain.entity.WebhookEvent;
import prac.fin.payment.domain.repository.WebhookEventRepository;
import prac.fin.webhook.service.dto.WebhookDeliveryStatus;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookDispatcher {
	
	private static final Duration[] BACKOFF = {
	        Duration.ZERO,
	        Duration.ofMinutes(1),
	        Duration.ofMinutes(5),
	        Duration.ofHours(1)
	};

	private final ObjectMapper objectMapper;
	private final WebhookDeliveryService deliveryService;
	private final WebhookEventRepository webhookEventRepository;
	
	/**
	 * Attempts delivery of a single webhook event
	 * 
	 * @param event
	 */
	public void dispatch(WebhookEvent event) {
		try {
			String webhookUrl = event.getMerchant().getWebhookUrl();
			if (webhookUrl == null || webhookUrl.isBlank()) {
	            log.warn("Merchant {} has no webhook URL configured. Skipping event {}",
	                    event.getMerchant().getId(), event.getId());
	            event.markDelivered(0);
	            webhookEventRepository.save(event);
	            return;
	        }
			String payloadJson = objectMapper.writeValueAsString(event.getPayload());
			
			WebhookDeliveryStatus status = deliveryService.deliver(webhookUrl, payloadJson, 
					event.getMerchant().getId().toString(), event.getEventType(), event.getAttemptCount() + 1);
			if(status.isDelivered()) {
				event.markDelivered(status.getStatusCode());
				log.info("Webhook delivered: eventId={} merchantId={} attempt={} status={}",
                        event.getId(), event.getMerchant().getId(),
                        event.getAttemptCount(), status.getStatusCode());
			} else {
				scheduleRetry(event, status.getStatusCode());
			}
			
		} catch (Exception e) {
            log.error("Webhook delivery error: eventId={}", event.getId(), e);
            scheduleRetry(event, 0);
        }
		
		webhookEventRepository.save(event);
	}
	
	private void scheduleRetry(WebhookEvent event, int code) {
		if (event.isExhausted()) {
            log.error("Webhook exhausted all retries: eventId={} merchantId={}",
                    event.getId(), event.getMerchant().getId());
            return;
        }
		
		int nextIndex = Math.min(event.getAttemptCount() + 1, BACKOFF.length - 1);
        Instant nextAttempt = Instant.now().plus(BACKOFF[nextIndex]);
		event.setNextAttemptAt(nextAttempt);
		
		event.recordFailedAttempt(code, nextAttempt);
		
		log.warn("Webhook delivery failed. Retry scheduled: eventId={} attempt={} nextAttemptAt={}",
	                event.getId(), event.getAttemptCount(), nextAttempt);
		
		webhookEventRepository.save(event);
	}
}
