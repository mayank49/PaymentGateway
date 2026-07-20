package prac.fin.payment.common.exception;

/**
 * Thrown when the merchant sends a request that fails business validation.
 *
 * This is a 400 Bad Request scenario. The caller's fault, not ours.
 *
 * Examples:
 *   - amount is 0 or negative
 *   - currency code is not a valid ISO 4217 code
 *   - required field is missing
 *
 * The 'field' tells the merchant exactly which part of their request is wrong.
 * This is how Stripe structures their errors, specific and actionable.
 *
 * Usage:
 *   throw new InvalidRequestException("amount", "Must be greater than 0");
 *   throw new InvalidRequestException("currency", "INR, USD, EUR are supported");
 */
public class InvalidRequestException extends PaymentGatewayException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 5065311666851202609L;
	
	private final String field;
	
	public InvalidRequestException(String message, String field) {
		super(message);
		this.field = field;
	}
	
	public String getField() {
        return field;
    }
}
