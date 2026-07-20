package prac.fin.payment.common.enums;

/**
 * Status of a checkout session stored in Redis.
 *
 * Sessions are temporary — they exist only to bridge the gap between
 * the merchant creating a PaymentIntent and the customer submitting payment.
 *
 * Why Redis and not Postgres?
 *   Sessions are short-lived (30 min TTL) and read on every page load
 *   of the checkout page. Redis handles TTL natively and is much faster
 *   for this kind of ephemeral, high-read data.
 *
 * Why do we need explicit states if Redis TTL already expires them?
 *   TTL handles EXPIRED automatically.
 *   But USED is something only your code knows — Redis has no way to know
 *   the customer already submitted their card. Without USED, a customer
 *   could hit "Pay" twice in quick succession and charge themselves twice.
 *
 * Flow:
 *   Merchant creates session  →  ACTIVE
 *   Customer submits card     →  USED    (locked immediately, before processor call)
 *   30 minutes pass           →  EXPIRED (Redis TTL removes the key entirely)
 */
public enum SessionStatus {

    /** Session is valid. Customer can submit payment. */
    ACTIVE,

    /**
     * Customer already submitted payment on this session.
     * Any further submission attempts must be rejected with 409 Conflict.
     * This is your first line of defense against double charges.
     */
    USED,

    /**
     * Session TTL elapsed before customer completed checkout.
     * The customer will need to go back to the merchant and start over.
     * In practice, Redis deletes the key — but we set this explicitly
     * when we detect an expired session during a request.
     */
    EXPIRED
}
