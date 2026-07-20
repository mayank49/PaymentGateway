package prac.fin.payment.common.enums;

/**
 * Status of an individual Transaction — one attempt to move money via a processor.
 *
 * A single PaymentIntent can have multiple Transactions:
 *   - First attempt fails 3DS       → Transaction(FAILED)
 *   - Customer retries with OTP     → Transaction(CAPTURED)
 *   - Merchant later refunds        → Transaction(REFUNDED)  ← new row, not an update
 *
 * Important: we never UPDATE a failed transaction row to fix it.
 * We create a NEW transaction row for each attempt.
 * This gives us an immutable audit trail — every attempt is preserved.
 *
 * State transitions:
 *
 *  PENDING
 *     ├──► AUTHORIZED           (bank placed a hold, two-step flow)
 *     ├──► CAPTURED             (bank took funds, one-step flow)
 *     ├──► FAILED               (hard decline from processor/bank)
 *     └──► PENDING_VERIFICATION (request sent, no response — timeout)
 *
 *  AUTHORIZED
 *     ├──► CAPTURED             (merchant triggered capture)
 *     └──► FAILED               (capture failed or auth expired)
 *
 *  CAPTURED
 *     └──► REFUNDED             (full or partial refund issued)
 *
 *  PENDING_VERIFICATION
 *     ├──► CAPTURED             (reconciliation confirmed money moved)
 *     └──► FAILED               (reconciliation confirmed nothing moved)
 *
 *  FAILED, REFUNDED             → terminal states
 */
public enum TransactionStatus {

	/** Created in our DB, not yet sent to the processor. */
    PENDING,

    /**
     * Bank approved and placed a hold on the funds.
     * Money hasn't moved yet — it's reserved.
     * Used in auth-only / two-step flows (e.g. hotels, car rentals).
     */
    AUTHORIZED,

    /**
     * Funds captured from the customer's account.
     * This is the "money has moved" state.
     */
    CAPTURED,

    /** Processor or bank returned a definitive failure. */
    FAILED,

    /**
     * We sent the request to the processor but received no response.
     * The bank may or may not have debited the customer.
     * Do NOT mark this as FAILED — you could be wrong.
     * Reconciliation service will resolve this to CAPTURED or FAILED.
     */
    PENDING_VERIFICATION,

    /**
     * A refund was issued against this transaction.
     * Note: refunds create a NEW transaction row pointing back
     * to this one via parent_transaction_id. This row is just
     * updated to REFUNDED to reflect its final state.
     */
    REFUNDED
}
