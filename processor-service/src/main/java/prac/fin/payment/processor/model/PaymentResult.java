package prac.fin.payment.processor.model;

import lombok.Builder;
import lombok.Getter;
import prac.fin.payment.common.enums.TransactionStatus;

/**
 * The result of any processor operation. It can be charge, refund, or status check.
 *
 * Internal model. Never leaves the processor-service.
 * The ProcessorService translates this into Transaction status updates
 * and PaymentIntent status transitions.
 */
@Getter
@Builder
public class PaymentResult {
	
	private final TransactionStatus status;
	
	private final String processorPaymentId;
	
	private final String errorCode;
	
	private final String errorMessage;
	
	/**
	 * For 3DS payments, the URL the customer must be redirected to for OTP.
	 * Present when status = REQUIRES_ACTION. Null otherwise.
	 * For Razorpay: extracted from next[0].url
	 * For Stripe:   extracted from next_action.redirect_to_url.url
	 * For PayU:     returned in the initial response
	 */
    private final String redirectUrl;
	
	public static PaymentResult captured(String processorPaymentId) {
        return PaymentResult.builder()
                .status(TransactionStatus.CAPTURED)
                .processorPaymentId(processorPaymentId)
                .build();
    }

    public static PaymentResult failed(String errorCode, String errorMessage) {
        return PaymentResult.builder()
                .status(TransactionStatus.FAILED)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .build();
    }

    public static PaymentResult pendingVerification() {
        return PaymentResult.builder()
                .status(TransactionStatus.PENDING_VERIFICATION)
                .build();
    }
    
    public static PaymentResult refunded(String processorRefundId) {
        return PaymentResult.builder()
                .status(TransactionStatus.REFUNDED)
                .processorPaymentId(processorRefundId)
                .build();
    }
    
    /**
     * 3DS OTP required. Customer must be redirected to redirectUrl.
     * Maps to IntentStatus.REQUIRES_ACTION.
     */
    public static PaymentResult requiresAction(String processorPaymentId, String redirectUrl) {
        return PaymentResult.builder()
                .status(TransactionStatus.PENDING)
                .processorPaymentId(processorPaymentId)
                .redirectUrl(redirectUrl)
                .build();
    }
}
