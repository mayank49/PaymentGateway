package prac.fin.payment.common.exception;

/**
 * Thrown when the external payment processor (Razorpay) is unavailable,
 * times out, or returns an unexpected error unrelated to the payment itself.
 *
 * This is a 503 Service Unavailable. It's our infrastructure's problem,
 * not the customer's card. The merchant should retry later.
 *
 * This is different from PaymentDeclinedException:
 *   PaymentDeclinedException  → Razorpay was reachable, bank said NO
 *   ProcessorException        → Razorpay itself could not be reached,
 *                               or returned a 5xx error
 *
 * When this is thrown, the PaymentIntent status should be set to UNKNOWN
 * (not FAILED) because we don't know if the bank was reached or not.
 * The reconciliation service will resolve it later.
 *
 * Usage:
 *   throw new ProcessorException("razorpay", "Connection timed out after 30s", cause);
 */
public class ProcessorException extends PaymentGatewayException {

 	/**
	 * 
	 */
	private static final long serialVersionUID = -1744287506055761306L;
	
	private final String processor;

    public ProcessorException(String processor, String message) {
        super(message);
        this.processor = processor;
    }

    public ProcessorException(String processor, String message, Throwable cause) {
        super(message, cause);
        this.processor = processor;
    }

    public String getProcessor() {
        return processor;
    }
}
