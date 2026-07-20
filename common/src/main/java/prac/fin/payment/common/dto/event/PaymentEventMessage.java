package prac.fin.payment.common.dto.event;

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
public class PaymentEventMessage {
	
	//Topic name constants
	
	public static final String TOPIC_SUCCEEDED = "payment.succeeded";
    public static final String TOPIC_FAILED  = "payment.failed";
    public static final String TOPIC_UNKNOWN  = "payment.unknown";
    public static final String TOPIC_ACTION_REQUIRED = "payment.action_required";

	/**
	 * Which topic/event type this message represents.
	 */
	private String eventType;
	
	/**
	 * pi_<uuid>
	 */
	private String intentId;
	
	private String merchantId;
	
	private Long amount;
	
	private String currency;
	
	private IntentStatus status;
	
	private PaymentMethodType paymentMethodType;
	
	private String processorPaymentId;
	
	/**
     * For 3DS payments that the URL the customer must be redirected to.
     * Only present when eventType = PAYMENT_ACTION_REQUIRED.
     */
	private String redirectUrl;
	
	/**
	 * Merchant's custom metadata.
	 * Passed back as is to the merchant.
	 */
	private Map<String, Object> metadata;
	
	private String merchantWebhookUrl;
	
	private Instant occurredAt;
	
	
}
