package prac.fin.payment.common.exception;

import prac.fin.payment.common.enums.IntentStatus;

/**
 * Thrown when code attempts to move a PaymentIntent into a state
 * that is not a valid transition from its current state.
 *
 * This is a 409 Conflict. The request is valid on its own but
 * conflicts with the current state of the resource.
 *
 * Examples:
 *   - Trying to mark a SUCCEEDED intent as FAILED
 *   - Trying to move straight from REQUIRES_PAYMENT_METHOD to SUCCEEDED
 *     (must go through PROCESSING first)
 *   - Trying to cancel an already PROCESSING intent
 *
 * This should be thrown immediately after calling canTransitionTo():
 *
 *   if (!intent.getStatus().canTransitionTo(SUCCEEDED)) {
 *       throw new InvalidStateTransitionException(
 *           intent.getId().toString(),
 *           intent.getStatus(),
 *           IntentStatus.SUCCEEDED
 *       );
 *   }
 */
public class InvalidStateTransitionException extends PaymentGatewayException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 4717887424133555000L;

	public InvalidStateTransitionException(
            String intentId,
            IntentStatus from,
            IntentStatus to) {
        super(String.format(
            "PaymentIntent [%s] cannot transition from %s to %s",
            intentId, from, to
        ));
    }
}
