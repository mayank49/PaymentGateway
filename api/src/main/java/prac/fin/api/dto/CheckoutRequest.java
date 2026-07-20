package prac.fin.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CheckoutRequest {
	
	 private String cardNumber;
	 private Integer expiryMonth;
     private Integer expiryYear;
     private String  cvv;
     private String  cardHolderName;

     private String upiId;

     @NotBlank
     private String email;

     @NotBlank
     private String phone;

     /**
      * Pre-created order reference at the processor.
      * Required for Razorpay. Must create an Order via Razorpay API
      * before calling this endpoint.
      * Not required for PayU or Stripe.
      */
     private String processorOrderRef;
     
     /** 3DS 2.0 browser fingerprint. Collected by JS on our checkout page. */
     private BrowserInfoRequest browserInfo;
}
