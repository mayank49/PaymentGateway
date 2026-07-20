package prac.fin.payment.common.exception;

/**
 * Thrown when the payment processor or bank definitively declines a payment.
 *
 * This is a 422 Unprocessable Entity. The request was well-formed and
 * our system processed it correctly, but the bank refused the transaction.
 * This is NOT our fault and NOT the merchant's fault, it's the customer's
 * card/bank that rejected it.
 *
 * The processorCode comes directly from the bank/Razorpay. Passing it through
 * lets the merchant show meaningful error messages to the customer:
 *
 *   "INSUFFICIENT_FUNDS"     → "Your card has insufficient funds"
 *   "CARD_EXPIRED"           → "Your card has expired"
 *   "DO_NOT_HONOUR"          → "Your bank declined this transaction"
 *   "INVALID_CVV"            → "The CVV you entered is incorrect"
 *
 * Usage:
 *   throw new PaymentDeclinedException("INSUFFICIENT_FUNDS", "Transaction declined by bank");
 */
public class PaymentDeclinedException extends PaymentGatewayException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 7298208240069445959L;
	
	/** The error code from the processor (Razorpay) or bank. */
    private final String processorCode;

    public PaymentDeclinedException(String processorCode, String message) {
        super(message);
        this.processorCode = processorCode;
    }

    public String getProcessorCode() {
        return processorCode;
    }
}
