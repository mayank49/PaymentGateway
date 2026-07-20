package prac.fin.payment.session.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import prac.fin.payment.common.enums.SessionStatus;

/**
 * Represents a checkout session stored in Redis.
 * 
 * It's serialized to JSON and stored under the key: session:{sessionId}
 * Redis TTL is set to 30 minutes on write. After that, Redis
 * automatically deletes the key
 * 
 * WHAT THIS HOLDS:
 * Everything the checkout page needs to render itself without
 * making additional service calls
 *   - Which intent this session is for (intentId)
 *   - Which merchant owns it (merchantId) for ownership verification
 *   - Where to redirect after success/cancel
 *   - Its own expiry time (expiresAt) for display purposes
 *   
 * Implements Serializable as a safety measure. Some Redis
 * configurations fall back to Java serialization.
 * Jackson JSON serialization is what we actually use.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutSession implements Serializable {

	/** sess_<uuid> — the session's own identifier. */
	private String sessionId;
	
	private String intentId;
	
	private UUID merchantId;
	
	@Builder.Default
	private SessionStatus status = SessionStatus.ACTIVE;
	
	private String successUrl;
	
	private String cancelUrl;
	
	private Instant createdAt;
	
	private Instant expiresAt;
	
	public boolean isUsable() {
		return status == SessionStatus.ACTIVE
				&& Instant.now().isBefore(expiresAt);
	}
	
	public void markUsed() {
		this.status = SessionStatus.USED;
	}
}
