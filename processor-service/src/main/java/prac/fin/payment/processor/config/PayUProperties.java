package prac.fin.payment.processor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;


/**
 * Binds to the 'payu' block in application.yml:
 *
 * payu:
 *   merchant-key: ${PAYU_MERCHANT_KEY}
 *   salt: ${PAYU_SALT}
 *   payment-url: https://secure.payu.in/_payment        # prod
 *   refund-url: https://info.payu.in/merchant/postservice.php?form=2
 *   verify-url: https://info.payu.in/merchant/postservice.php?form=2
 *   success-url: https://pay.gateway.com/webhooks/payu/success
 *   failure-url: https://pay.gateway.com/webhooks/payu/failure
 *
 * For test environment, use:
 *   payment-url: https://test.payu.in/_payment
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "payu")
public class PayUProperties {

	private String merchantKey;
    private String salt;
    private String paymentUrl;
    private String refundUrl;
    private String verifyUrl;

    /**
     * PayU posts the result to this URL on successful payment.
     * Must be a publicly accessible HTTPS URL.
     */
    private String successUrl;

   /**
    * PayU posts the result to this URL on failed payment.
    */
    private String failureUrl;
}
