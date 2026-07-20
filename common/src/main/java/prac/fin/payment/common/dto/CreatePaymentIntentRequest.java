package prac.fin.payment.common.dto;

import java.util.Map;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import prac.fin.payment.common.enums.PaymentMethodType;

/**
 * Request body for POST /v1/payment-intents
 *
 * Sent by the merchant's backend when a customer initiates checkout.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePaymentIntentRequest {
	
	/**
	 * Amount is smallest currency unit (paise for INR, cents for USD)
	 * Minimum 1 - we reject zero-amount payments
	 */
	@NotNull(message = "Amount is required")
	@Min(value = 1, message = "Amount must be at least 1 in smallest currency unit")
	private Long amount;
	
	/**
     * ISO 4217 currency code. Exactly 3 uppercase letters.
     * Examples: INR, USD, EUR, GBP
     */
	@NotBlank(message = "Currency is required")
	@Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO 4217 code e.g. INT, USD")
	private String currency;
	
	/**
     * Merchant-generated unique key for this payment attempt.
     */
	@NotBlank(message = "Idempotency key is required")
	@Size(max = 255, message = "Idempotency key must not exceed 255 characters")
	private String idempotencyKey;
	
	 /**
     * Optional. If the merchant already knows how the customer wants to pay.
     * If null, the checkout page will show all available payment methods.
     */
	private PaymentMethodType paymentMethodType;
	
	/**
     * Optional. free-form data the merchant wants to attach.
     * Stored as JSONB. Returned as-is on every response.
     * Useful for: order ID, customer email, product name, etc.
     * We don't read or validate this. It's the merchant's data.
     */
	private Map<String, Object> metadata;
}
