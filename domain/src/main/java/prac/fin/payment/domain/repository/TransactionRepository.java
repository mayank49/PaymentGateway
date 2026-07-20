package prac.fin.payment.domain.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import prac.fin.payment.common.enums.TransactionStatus;
import prac.fin.payment.domain.entity.Transaction;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
	
	/**
	 * Load all transaction attempts for a given intent.
	 * 
	 * @param intentId
	 * @return
	 */
	List<Transaction> findByIntentIdOrderByCreatedAtAsc(UUID intentId);
	
	/**
	 * Look up a transaction by Razorpay's own ID.
	 * 
	 * @param processorTxnId
	 * @return
	 */
	Optional<Transaction> findByProcessorTxnId(String processorTxnId);
	
	
	List<Transaction> findByCreatedAtAndStatus(Instant createdAt, TransactionStatus status);

	/**
	 * Find the most recent captured transaction for an intent
	 * TOP in Spring Data = LIMIT 1 in SQL
	 * 
	 * @param intentId
	 * @param status
	 * @return
	 */
	Optional<Transaction> findTopByIntentIdAndStatusOrderByCreatedAtDesc(UUID intentId, TransactionStatus status);
	
	/**
	 * Reconciliation: find all transactions that timed out and were
     * left in PENDING_VERIFICATION within a date range.
	 * 
	 * @param from
	 * @param to
	 * @return
	 */
	@Query("""
		SELECT * FROM Transaction t
		WHERE t.status = 'PENDING_VERIFICATION'
		AND t.createdAt BETWEEN :from AND :to
		ORDER BY t.createdAt ASC		
	""")
	List<Transaction> findPendingVerificationBetween(@Param("from") Instant from, @Param("to") Instant to);
}
