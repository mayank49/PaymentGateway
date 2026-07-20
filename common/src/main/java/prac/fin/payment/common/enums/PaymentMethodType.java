package prac.fin.payment.common.enums;

/**
 * The payment method a customer used to complete checkout.
 *
 * Why does this matter beyond just labeling?
 * The processor-service uses this to decide HOW to route the payment:
 *
 *   CARD        → Visa/Mastercard network → Acquiring Bank → Issuing Bank
 *   UPI         → NPCI (National Payments Corporation of India) network
 *   NET_BANKING → Direct redirect to customer's bank portal
 *   WALLET      → Paytm, PhonePe, Amazon Pay etc.
 *   EMI         → Installment plan, usually routed through the card network
 *                 but with special instructions to the bank
 *
 * Each method has a different:
 *   - Authorization flow (card needs CVV, UPI needs PIN/OTP, net banking needs redirect)
 *   - Fee structure (UPI is free in India by regulation, cards charge 2-3%)
 *   - Refund timeline (wallets are instant, net banking takes 3-5 days)
 *
 * For now, Razorpay supports all of these — we just pass the method
 * and Razorpay handles the routing internally.
 */
public enum PaymentMethodType {

    /**
     * Credit or debit card.
     * Requires: card number, expiry, CVV.
     * Networks: Visa, Mastercard, Amex, RuPay.
     * May trigger 3D Secure (OTP) — hence REQUIRES_ACTION on IntentStatus.
     */
    CARD,

    /**
     * Unified Payments Interface — India's real-time payment system.
     * Requires: UPI ID (e.g. customer@upi) or QR scan.
     * Settled instantly. Zero fees by regulation.
     */
    UPI,

    /**
     * Customer logs into their bank's portal to approve the payment.
     * Requires: bank selection, then redirect to bank's website.
     * Slower UX but high trust factor for large amounts.
     */
    NET_BANKING,

    /**
     * Digital wallets — pre-loaded balance.
     * Examples: Paytm, PhonePe, Amazon Pay, Mobikwik.
     * Fast checkout since customer already has funds loaded.
     */
    WALLET,

    /**
     * Equated Monthly Installments — customer pays in parts over 3/6/12 months.
     * Routed through the card network but with EMI instructions to the issuing bank.
     * The gateway collects full amount upfront; bank handles the installment split.
     */
    EMI
}
