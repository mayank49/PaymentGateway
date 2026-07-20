package prac.fin.payment.processor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * Binds to the 'razorpay' block in application.yml.
 * 
 * The actual values should come from environment variables
 * in production and never hardcoded in application.yml:
 *   key-id: ${RAZORPAY_KEY_ID}
 *   key-secret: ${RAZORPAY_KEY_SECRET}
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "razorpay")
public class RazorpayProperties {

	/**
	 * Razorpay key id
	 */
	private String keyId;
	
	/**
	 * Razorpay key secret
	 */
	private String keySecret;
	
	/**
	 * Separate secret for verifying incoming Razorpay webhooks.
	 */
	private String webhookSecret;
	
	/**
     * URL Razorpay POSTs to after the customer completes OTP.
     * Contains razorpay_payment_id, razorpay_order_id, razorpay_signature.
     * Must be a publicly accessible HTTPS URL.
     * Example: https://pay.gateway.com/webhooks/razorpay/callback
     */
    private String callbackUrl;
}
