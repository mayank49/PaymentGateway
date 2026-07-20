package prac.fin.payment.common.enums;

/**
 * Represents every possible state a PaymentIntent can be in.
 */
public enum IntentStatus {
	
	REQUIRES_PAYMENT_METHOD,
    PROCESSING,
    SUCCEEDED,
    FAILED,
    REQUIRES_ACTION,
    UNKNOWN,
    CANCELED;
	
	/**
	 * Guards against illegal state transitions anywhere in the codebase.
	 *
	 * Usage:
	 *   if (!intent.getStatus().canTransitionTo(SUCCEEDED)) {
	 *       throw new InvalidStateTransitionException(...)
	 *   }
	 */
	public boolean canTransitionTo(IntentStatus next) {
		return switch (this) {
			case REQUIRES_PAYMENT_METHOD -> next == PROCESSING || next == CANCELED;
			case PROCESSING              -> next == SUCCEEDED
                    || next == FAILED
                    || next == REQUIRES_ACTION
                    || next == UNKNOWN;
			case REQUIRES_ACTION         -> next == PROCESSING || next == FAILED;
			case UNKNOWN                 -> next == SUCCEEDED || next == FAILED;
			case SUCCEEDED,
			FAILED,
			CANCELED               -> false;
			default -> throw new IllegalArgumentException("Unexpected value: " + this);
		};
	}
}
