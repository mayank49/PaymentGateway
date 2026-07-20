package prac.fin.payment.common.dto;

import java.time.Instant;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import prac.fin.payment.common.enums.IntentStatus;
import prac.fin.payment.common.enums.PaymentMethodType;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentIntentResponse {
	
	/**
	 * pi_<uuid>
	 * 
	 * In the database (entity), the primary key will be UUID
	 * In the DTO (what we return to merchants), it's a String, 
	 * but it's not a raw UUID. It's prefixed: pi_550e8400-e29b-41d4-a716-446655440000.
	 * 
	 * The reason is purely readability and debuggability.
	 * 
	 * Stripe does exactly this — pi_, cs_, ch_, re_ prefixes on everything.
	 */
	private String id;
	
	private Long amount;
	
	private String currency;
	
	private IntentStatus status;
	
	private String clientSecret;
	
	private PaymentMethodType paymentMethodType;

    private Map<String, Object> metadata;

    private Instant createdAt;

    private Instant updatedAt;
}
