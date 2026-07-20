package prac.fin.payment.domain.entity;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import prac.fin.payment.common.enums.TransactionStatus;

import java.util.UUID;

/**
 * Records a single attempt to move money through an external processor (Razorpay).
 *
 * IMMUTABILITY RULE:
 *   We never update a transaction row to "fix" it after a failure.
 *   Every retry = a new Transaction row.
 *   Every refund = a new Transaction row (with parentTransaction pointing back).
 *   This gives a complete, ordered audit trail of everything that happened.
 *   
 * RELATIONSHIP TO PAYMENT INTENT:
 *   Many Transactions can belong to one PaymentIntent:
 *   
 * PaymentIntent [pi_abc]
 *     └── Transaction [txn_1] FAILED        ← first attempt, bank declined
 *     └── Transaction [txn_2] CAPTURED      ← customer retried, succeeded
 *     └── Transaction [txn_3] REFUNDED      ← merchant issued refund later
 *                  parentTransaction = txn_2 ↑
 */
@Entity
@Table(
    name = "transaction",
    indexes = {
        // Most lookups are "all transactions for this intent"
        @Index(name = "idx_txn_intent_id", columnList = "intent_id"),
        // Reconciliation matches our records against Razorpay's by processorTxnId
        @Index(name = "idx_txn_processor_txn_id", columnList = "processor_txn_id"),
        // Reconciliation also queries by date range and status
        @Index(name = "idx_txn_created_at", columnList = "created_at"),
        @Index(name = "idx_txn_status", columnList = "status")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction extends BaseEntity {
	
	@Id
	@GeneratedValue
	@Column(name = "id", updatable = false, nullable = false)
    private UUID id;
	
	/**
     * The intent this transaction belongs to.
     * We don't need intent details every time we load a transaction.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "intent_id", nullable = false)
    private PaymentIntent paymentIntent;
    
    /**
     * Which processor handled this attempt.
     * Hardcoded to "razorpay" for now.
     */
    @Column(name = "processor", nullable = false, length = 50)
    private String processor;

    /**
     * Razorpay's own ID for this payment. Format: pay_xxxxxxxxxxxxxxxx
     * Null until Razorpay responds. If we timed out, this may stay null
     * in that case status will be PENDING_VERIFICATION.
     *
     * This is the ID you use when calling Razorpay's refund API.
     */
    @Column(name = "processor_txn_id", length = 255)
    private String processorTxnId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private TransactionStatus status = TransactionStatus.PENDING;
    
    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;
    
    /**
     * Bank's authorization code. Returned on successful AUTHORIZED status.
     * Needed to capture funds in a two-step auth/capture flow.
     * Null for one-step captures (which is what Razorpay does by default).
     */
    @Column(name = "auth_code", length = 20)
    private String authCode;
    
    /**
     * Machine-readable error code from Razorpay on failure.
     * Examples: "BAD_REQUEST_ERROR", "GATEWAY_ERROR", "SERVER_ERROR"
     * Used to map to our PaymentDeclinedException with the right processorCode.
     */
    @Column(name = "error_code", length = 100)
    private String errorCode;
    
    /**
     * Human-readable failure description from Razorpay.
     */
    @Column(name = "error_message", length = 500)
    private String errorMessage;
    
    /**
     * For refund transactions only. It points to the original captured transaction.
     * Null for all non-refund transactions.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_transaction_id")
    private Transaction parentTransaction;
}
