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
import prac.fin.payment.common.enums.ReconciliationStatus;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * One reconciliation record per transaction per daily settlement run.
 * 
 * Every night Razorpay generates a settlement file containing every
 * transaction they processed that day. Our ReconciliationService:
 *
 *   1. Downloads the settlement file from Razorpay
 *   2. Loads all our Transaction rows for the same date range
 *   3. Matches them by processorTxnId (our txn.processorTxnId == their payment_id)
 *   4. For each match, writes a MerchantLedger row with the result
 *   
 *   MATCHED     → amounts agree, statuses agree. All good.
 *   UNMATCHED   → in our DB but missing from Razorpay's file.
 *                 Could mean: we created a transaction record but
 *                 the request never actually reached Razorpay.
 *   DISCREPANCY → both sides have it but something differs.
 *                 Could mean: customer was charged but we marked it failed,
 *                 or amounts don't match (very serious).
 */
@Entity
@Table(
    name = "merchant_ledger",
    indexes = {
        @Index(name = "idx_ledger_merchant_id", columnList = "merchant_id"),
        @Index(name = "idx_ledger_transaction_id", columnList = "transaction_id"),
        // Reconciliation queries always filter by settlement date
        @Index(name = "idx_ledger_settlement_date", columnList = "settlement_date"),
        // Finding all unresolved discrepancies
        @Index(name = "idx_ledger_status", columnList = "status"),
        // Grouping by which processor sent the file
        @Index(name = "idx_ledger_processor_id", columnList = "processor_id")
    }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantLedger extends BaseEntity {

	@Id
    @GeneratedValue
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;
    
    /**
     * The transaction we are reconciling.
     * We join on transaction.processorTxnId == Razorpay's payment_id.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;
    
    /**
     * Razorpay's payment ID from the settlement file.
     * Should match transaction.processorTxnId if MATCHED.
     * Null if UNMATCHED (Razorpay has no record of this transaction).
     */
    @Column(name = "processor_txn_id", length = 255)
    private String processorTxnId;
    
    /**
     * Amount recorded in our Transaction table (paise).
     * This is what WE think was charged.
     */
    @Column(name = "internal_amount")
    private Long internalAmount;

    /**
     * Amount from Razorpay's settlement file (paise).
     */
    @Column(name = "processor_amount")
    private Long processorAmount;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private ReconciliationStatus status = ReconciliationStatus.UNMATCHED;
    
    @Column(name = "settlement_date")
    private LocalDate settlementDate;
    
    @Column(name = "processor_id", length = 50)
    private String processorId;
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "processor_data", columnDefinition = "jsonb")
    private Map<String, Object> processorData;
    
    /**
     * Is there a money mismatch between what we recorded and what Razorpay says?
     * The most critical check in reconciliation.
     */
    public boolean hasAmountMismatch() {
        return internalAmount != null
            && processorAmount != null
            && !internalAmount.equals(processorAmount);
    }
}
