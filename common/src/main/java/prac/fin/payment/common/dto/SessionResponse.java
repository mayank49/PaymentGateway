package prac.fin.payment.common.dto;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionResponse {

	/** sess_<uuid> the session identifier. */
    private String sessionId;

    /** The PaymentIntent this session is tied to. */
    private String intentId;

    /**
     * The URL the merchant redirects their customer to.
     * Format: https://pay.gateway.com/checkout/sess_<id>
     * Valid for 30 minutes.
     */
    private String checkoutUrl;

    /** Where to redirect the customer after successful payment. */
    private String successUrl;

    /** Where to redirect the customer if they cancel. */
    private String cancelUrl;

    /** When this session stops being valid. */
    private Instant expiresAt;
}
