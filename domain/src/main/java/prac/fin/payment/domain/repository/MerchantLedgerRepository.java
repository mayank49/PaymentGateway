package prac.fin.payment.domain.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import prac.fin.payment.common.enums.ReconciliationStatus;
import prac.fin.payment.domain.entity.MerchantLedger;

public interface MerchantLedgerRepository extends JpaRepository<MerchantLedger, UUID> {
	
	/**
	 * Load all reconciliation records for a settlement date.
	 * 
	 * @param settlementDate
	 * @return
	 */
	List<MerchantLedger> findBySettlementDate(LocalDate settlementDate);
	
	/**
	 * Find all unresolved problems for a specific merchant.
	 * 
	 * @param merchantId
	 * @param status
	 * @return
	 */
	List<MerchantLedger> findByMerchantIdAndStatus(UUID merchantId, ReconciliationStatus status);
	
	/**
	 * Prevents the reconciliation job from creating duplicate ledger rows
     * if it runs twice for the same date (e.g. after a crash and restart).
	 * 
	 * @param transactionId
	 * @return
	 */
    boolean existsByTransactionId(UUID transactionId);
}
