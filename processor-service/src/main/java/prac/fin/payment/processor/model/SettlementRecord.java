package prac.fin.payment.processor.model;

import java.time.LocalDate;

/**
 * A single row from a processor's settlement file, normalised to a
 * common format regardless of which processor produced it.
 * 
 */
public record SettlementRecord (

	 /** Processor's payment ID. Matches Transaction.processorTxnId */
    String processorPaymentId,

    /** Amount in smallest currency unit (paise) */
    Long amount,

    /** "captured", "refunded", "failed" */
    String status,

    LocalDate settlementDate) {
}
