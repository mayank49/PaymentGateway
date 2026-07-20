package prac.fin.payment.processor.model;

import lombok.Builder;
import lombok.Getter;

/**
 * Everything the processor needs to issue a refund.
 */
@Getter
@Builder
public class RefundRequest {
	
	private final String processorPaymentId;
	
	private final Long amount;
	
	private final String currency;
	
	private final String transactionId;
}
