package prac.fin.payment.processor.model;

import lombok.Builder;
import lombok.Getter;
import prac.fin.payment.common.enums.PaymentMethodType;

/**
 * Everything the processor needs to initiate a payment.
 *
 * The card data here is DECRYPTED. Retrieved from our Card Vault
 * by the ProcessorService just before calling the processor.
 * It should never be logged, cached, or stored anywhere after this point.
 *
 * This is an internal model. It never leaves the processor-service module.
 * The API layer never sees raw card data.
 */
@Getter
@Builder
public class PaymentRequest {

	 private final String intentId;
	 
	 private final String transactionId;
	 
	 private final Long amount;
	 
	 private final String currency;
	 
	 private final PaymentMethodType paymentMethodType;
	 
	 private final String cardNumber;

     private final Integer expiryMonth;

     private final Integer expiryYear;
     
     private final String cvv;
     
     private final String cardHolderName;
     
     private final String upiId;
     
     // Customer contact details
     // Required by PayU. Optional but useful for Razorpay receipts.
     private final String email;
     private final String phone;
     
     /**
      * Pre-created processor order reference.
      * Razorpay S2S requires an order_id created beforehand via their Orders API.
      * Null for PayU and Stripe (they don't use this).
      */
     private final String processorOrderRef;
     
     /**
      * Browser fingerprint collected by JS on our checkout page.
      * Required for 3DS 2.0 authentication by all three processors.
      * The checkout page JS collects these and POSTs them along with card details.
      */
     private final BrowserInfo browserInfo;
     
     /**
      * Browser fingerprint data for 3DS 2.0.
      * Collected on our checkout page via JavaScript.
      */
     @lombok.Builder
     @lombok.Getter
     public static class BrowserInfo {
         private final boolean javaEnabled;
         private final boolean javascriptEnabled;
         private final int timezoneOffset;
         private final int colorDepth;
         private final int screenWidth;
         private final int screenHeight;
         private final String userAgent;
         private final String language;
     }
}
