package prac.fin.payment.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks every webhook notification we need to send to a merchant.
 *
 * WHY PERSIST WEBHOOKS?
 *   If we just made an HTTP call inline after a payment, any failure
 *   (merchant server down, our server crashes mid-send) would mean
 *   the merchant never knows the payment succeeded.
 *
 *   Instead:
 *     1. Payment completes  → we write a WebhookEvent row (delivered = false)
 *     2. Scheduler polls    → finds undelivered rows, sends the HTTP POST
 *     3. Merchant responds  → we mark delivered = true
 *     4. Merchant is down   → we set nextAttemptAt = now + backoff, retry later
 * 
 * THE CRITICAL INDEX:
 *   The scheduler runs every 30 seconds and queries:
 *   WHERE delivered = false AND next_attempt_at <= NOW()
 *   The partial index on (next_attempt_at) WHERE delivered = false
 *   makes this query fast, it only indexes rows that still need work.
 *   Once delivered = true, the row drops out of the index automatically.
 */
@Entity
@Table(
    name = "webhook_event",
    indexes = {
        @Index(name = "idx_webhook_intent_id", columnList = "intent_id"),
        @Index(name = "idx_webhook_merchant_id", columnList = "merchant_id"),
        // The scheduler's query — only undelivered rows matter here
        @Index(name = "idx_webhook_next_attempt_at", columnList = "next_attempt_at")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookEvent {

	@Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;
	
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "intent_id", nullable = false)
    private PaymentIntent paymentIntent;
    
    /**
     * The event type tells the merchant what happened.
     * We use dot notation following Stripe's convention.
     *
     * Examples:
     *   "payment.succeeded"
     *   "payment.failed"
     *   "payment.action_required"
     *   "refund.created"
     */
    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;
    
    /**
     * The full JSON body we POST to the merchant's webhook URL.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> payload;
    
    @Builder.Default
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;
    
    @Builder.Default
    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = 4;
    
    /**
     * When we last tried to deliver this webhook.
     * Null if no attempt has been made yet.
     */
    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;
    
    /**
     * When the scheduler should next try delivery.
     * Set to NOW() at creation for immediate first attempt.
     * After each failure: set to NOW() + backoff interval.
     * Null after successful delivery (doesn't matter anymore).
     */
    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;
    
    @Builder.Default
    @Column(name = "delivered", nullable = false)
    private boolean delivered = false;
    
    /**
     * The HTTP status code returned by the merchant's server
     * on the most recent attempt.
     * Null if no attempt yet.
     * 200 → delivered. 500, timeout → will retry.
     */
    @Column(name = "http_status")
    private Integer httpStatus;
    
    /**
     * Has this webhook exhausted all retry attempts without success?
     * Used by the scheduler to skip rows that have no hope of delivery.
     */
    public boolean isExhausted() {
        return !delivered && attemptCount >= maxAttempts;
    }
    
    /**
     * Called by the scheduler after a successful delivery.
     */
    public void markDelivered(int httpStatus) {
        this.delivered = true;
        this.httpStatus = httpStatus;
        this.lastAttemptAt = Instant.now();
        this.nextAttemptAt = null;
        this.attemptCount++;
    }
    
    /**
     * Called by the scheduler after a failed attempt.
     * Sets the next retry time based on the backoff interval provided.
     */
    public void recordFailedAttempt(int httpStatus, Instant nextAttempt) {
        this.httpStatus = httpStatus;
        this.lastAttemptAt = Instant.now();
        this.nextAttemptAt = nextAttempt;
        this.attemptCount++;
    }
}
