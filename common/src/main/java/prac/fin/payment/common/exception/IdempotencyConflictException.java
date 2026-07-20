package prac.fin.payment.common.exception;

/**
 * Thrown when a merchant sends a request with an idempotency key
 * that was already used for a previous request.
 *
 * This is a 409 Conflict but it's not a true error.
 * It means: "We already processed this exact request. Here's the result."
 *
 * The merchant should NOT retry with the same key. Instead they should
 * fetch the existing PaymentIntent using the returned existingIntentId.
 *
 * Why does this exist?
 *   The merchant sends POST /payment-intents with idempotency key "order-123".
 *   Network drops. Merchant never gets the response.
 *   Merchant retries with the same key "order-123".
 *   Without this: two intents created → potential double charge.
 *   With this: we detect the duplicate and return the original intent ID.
 *
 * Usage:
 *   throw new IdempotencyConflictException(key, existingIntent.getId().toString());
 */
public class IdempotencyConflictException extends PaymentGatewayException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 5829713900976002565L;
	
	private final String existingIntentId;
	
	public IdempotencyConflictException(String idempotencyKey, String existingIntentId) {
        super("Idempotency key already used: " + idempotencyKey);
        this.existingIntentId = existingIntentId;
    }
	
	/**
     * The ID of the PaymentIntent that was created the first time
     * this idempotency key was used. Merchant should fetch this.
     */
    public String getExistingIntentId() {
        return existingIntentId;
    }
}
