package prac.fin.payment.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import prac.fin.payment.common.enums.IntentStatus;
import prac.fin.payment.domain.entity.PaymentIntent;

@Repository
public interface PaymentIntentRepository extends JpaRepository<PaymentIntent, UUID> {
	
	 /**
     * THE most important query in the system. It is called on every
     * POST /payment-intents request before creating anything.
     *
     * If this returns a value, we return the existing intent immediately
     * without creating a new one. This is the idempotency guarantee.
     */
	Optional<PaymentIntent> findByIdempotencyKey(String idempotencyKey);
	
	/**
     * Merchant dashboard, "show me all my payments, newest first".
     *
     * Returns a Page, not a List. This is important:
     * A merchant like Amazon could have millions of intents.
     * Returning a List would load them all into memory.
     * Page returns only the requested slice (e.g. page 0, size 20).
     *
     * Pageable is passed in by the caller:
     *   Pageable pageable = PageRequest.of(0, 20, Sort.by("createdAt").descending());
     */
	Page<PaymentIntent> findByMerchantId(UUID merchantId, Pageable pageable);
	
	/**
     * Merchant dashboard with status filter, "show me all my failed payments".
     */
    Page<PaymentIntent> findByMerchantIdAndStatus(
            UUID merchantId,
            IntentStatus status,
            Pageable pageable
    );
    
    /*
     * These are the rows that need to be compared against Razorpay's
     * settlement file to determine their true outcome.
     *
     * We use @Query here because the condition involves an IN clause
     * on an enum list. Spring Data's method name syntax can't express this
     * as cleanly.
     */
    @Query("""
    	SELECT * FROM PaymentIntent pi
    	WHERE pi.merchant.id = :merchantId
    	AND pi.status IN :statuses
    	ORDER BY pi.createdAt ASC
    """)
    List<PaymentIntent> findByMerchantIdAndStatusIn(@Param("merchantId") UUID merchantId,
            @Param("statuses") List<IntentStatus> statuses);
    
    /**
     * Atomic status update — used when transitioning intent state.
     * 
     * WHY THIS INSTEAD OF LOADING AND SAVING?
     *   The naive approach:
     *     PaymentIntent intent = repository.findById(id);
     *     intent.setStatus(SUCCEEDED);
     *     repository.save(intent);
     *
     *   The problem: between findById and save, another thread could
     *   have already changed the status. You'd silently overwrite it.
     *
     *   This query only updates if the current status matches what
     *   we expect (expectedStatus). It returns 1 if updated, 0 if not.
     *   If it returns 0, the caller knows a race condition occurred
     *   and can handle it appropriately.
     *
     * @Modifying tells Spring Data this query changes data (not a SELECT).
     * Without it, Spring would throw an exception at runtime.
     */
    @Modifying
    @Query("""
    	UPDATE PaymentIntent pi
    	SET pi.status = :newStatus
    	WHERE pi.id = :id
    	AND pi.status = :expectedStatus		
    """)
    int updateStatusIfEquals(
            @Param("id") UUID id,
            @Param("expectedStatus") IntentStatus expectedStatus,
            @Param("newStatus") IntentStatus newStatus
    );
}
