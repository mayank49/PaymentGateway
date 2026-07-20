package prac.fin.payment.processor.service;

import java.time.LocalDate;
import java.util.List;

import prac.fin.payment.processor.model.PaymentRequest;
import prac.fin.payment.processor.model.PaymentResult;
import prac.fin.payment.processor.model.RefundRequest;
import prac.fin.payment.processor.model.SettlementRecord;

/**
 * Contract that every payment processor must implement.
 *
 * This is the core of multi-processor support.
 * The ProcessorService doesn't know or care whether it's talking
 * to Razorpay, Stripe, or any other processor. It only knows this interface.
 *
 * To add a new processor:
 *   1. Create a new class that implements PaymentProcessor
 *   2. Register it as a Spring bean with the right processor name
 *   3. The ProcessorRouter will pick it up automatically
 *
 * Nothing else in the system changes.
 */
public interface PaymentProcessor {

	String getName();
	
	/**
     * Initiates a payment using the decrypted card details.
     *
     * The card data in PaymentRequest comes from our Card Vault.
     *
     * Returns a PaymentResult indicating what happened:
     *   CAPTURED : money held, all good
     *   FAILED : bank declined
     *   PENDING_VERIFICATION : we sent the request but got no response
     */
	PaymentResult charge(PaymentRequest request);
	
	/**
     * Refunds a previously captured payment.
     * amountToRefund can be less than the original for partial refunds.
     */
    PaymentResult refund(RefundRequest request);
    
    /**
     * Checks the current status of a payment directly with the processor.
     * Used by the reconciliation service for PENDING_VERIFICATION transactions.
     *
     * Returns the processor's current status for this payment ID.
     */
    PaymentResult checkStatus(String processorPaymentId);
    
    /**
     * Downloads and normalises the settlement file for a given date.
     * Each adapter translates their processor-specific format into
     * SettlementRecord so the reconciliation job is processor-agnostic.
     * 
     * @param date
     * @return
     */
    List<SettlementRecord> downloadSettlementFile(LocalDate date);
}
