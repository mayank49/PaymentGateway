package prac.fin.payment.processor.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds to the 'stripe' block in application.yml:
 *
 * stripe:
 *   secret-key: ${STRIPE_SECRET_KEY}      # sk_test_... or sk_live_...
 *   webhook-secret: ${STRIPE_WEBHOOK_SECRET}
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "stripe")
public class StripeProperties {
	
	/** Stripe secret key. starts with sk_test_ or sk_live_ */
    private String secretKey;

    /**
     * Stripe webhook signing secret. Used to verify incoming webhook POSTs.
     * Found in Stripe Dashboard -> Webhooks -> your endpoint -> Signing secret.
     */
    private String webhookSecret;

}
