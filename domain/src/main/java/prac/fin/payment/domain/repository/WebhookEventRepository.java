package prac.fin.payment.domain.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import prac.fin.payment.domain.entity.WebhookEvent;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID> {
	
	
	List<WebhookEvent> findByIntentIdOrderByCreatedAtDesc(UUID intentId);
	
	/**
     * THE scheduler query runs every 30 seconds.
     *
     * Finds all webhooks that:
     *   - Are not yet delivered
     *   - Haven't exhausted their retry attempts
     *   - Are due for their next attempt (nextAttemptAt is in the past)
     */
	@Query("""
		SELECT * FROM WebhookEvent w
		WHERE w.delivered = false
		AND w.attemptCount < w.maxAttempts
		AND w.nextAttemptAt <= :now
		ORDER BY w.nextAttemptAt ASC
	""")
	List<WebhookEvent> findDueForDelivery(@Param("now") Instant now);
	
	/**
	 * Finds the webhooks batch that:
     *   - Are not yet delivered
     *   - Haven't exhausted their retry attempts
     *   - Are due for their next attempt (nextAttemptAt is in the past)
     *   
     * FOR UPDATE allows to lock the batch for update.
     * SKIP UPDATE is native Postgres feature that allows skip the locked rows
     * by another scheduler instance.
	 * 
	 * @param now
	 * @param batchSize
	 * @return
	 */
	@Query(value = """
		SELECT * FROM WebhookEvent w
		WHERE w.delivered = false
		AND w.attemptCount < w.maxAttempts
		AND w.nextAttemptAt <= :now
		ORDER BY w.nextAttemptAt ASC
		LIMIT :batchSize
		FOR UPDATE
		SKIP LOCKED
	""", nativeQuery = true)
	List<WebhookEvent> claimRetryBatch(@Param("now") Instant now,
			@Param("batchSize") int batchSize);

}
