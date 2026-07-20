package prac.fin.payment.common.exception;

public class PaymentGatewayException extends RuntimeException {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -3269615998192450523L;

	public PaymentGatewayException(String message) {
		super(message);
	}
	
	public PaymentGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
