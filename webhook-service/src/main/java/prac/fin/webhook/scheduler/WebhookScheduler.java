package prac.fin.webhook.scheduler;

import java.time.Instant;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import prac.fin.payment.domain.entity.WebhookEvent;
import prac.fin.payment.domain.repository.WebhookEventRepository;
import prac.fin.webhook.service.WebhookDispatcher;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookScheduler {

	private static final int BATCH_SIZE = 10;
	
	private final WebhookDispatcher dispatcher;
	private final WebhookEventRepository webhookEventRepository;
	
	/**
	 * Claims and retries a batch of failed webhook events.
	 * 
	 * It locks the database rows so that other instances of the application
	 * do not work on the same rows.
	 * 
	 * @Transactional is required here because SELECT FOR UPDATE SKIP LOCKED
     * only holds the row locks for the duration of the transaction.
     * The lock is released when the transaction commits, which happens
     * after all dispatches in the batch are complete.
     * 
     * fixedDelay: 10s countdown starts after this method returns,
     * preventing overlap if a batch takes longer than 30 seconds.
	 */
	@Transactional
	@Scheduled(fixedDelay = 10000)
	public void retryWebhookEvents() {
		
		/*
		 * ---------------------------------------------------------
		 * 							WARNING
		 * ---------------------------------------------------------
		 * 
		 * This code may cause Database Connection pool starvation.
		 * 
		 * It locks the database rows until the transaction is 
		 * completed, which means until all rows in batch are 
		 * processed.
		 * 
		 * Also Read timeouts on Merchant for webhook deliveries 
		 * may cause severe delays.
		 * 
		 * Better approach would be to lock for query only
		 * and avoid duplicate processing using a database field.
		 * 
		 */
		List<WebhookEvent> events = webhookEventRepository.claimRetryBatch(Instant.now(), BATCH_SIZE);
		log.info("Webhook retry scheduler: claimed {} rows for retry", events.size());
		
		int succeeded = 0;
	    int rescheduled = 0;
		for(WebhookEvent event : events) {
			dispatcher.dispatch(event);
			if (event.isDelivered()) {
				succeeded++;
			} else {
				rescheduled++;
			}
		}
		
		log.info("Webhook retry batch complete: succeeded={} rescheduled={}", succeeded, rescheduled);
	}
}
