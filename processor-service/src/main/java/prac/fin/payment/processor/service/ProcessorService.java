package prac.fin.payment.processor.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import prac.fin.payment.common.enums.TransactionStatus;
import prac.fin.payment.common.exception.ProcessorException;
import prac.fin.payment.domain.entity.PaymentIntent;
import prac.fin.payment.domain.entity.Transaction;
import prac.fin.payment.domain.repository.TransactionRepository;
import prac.fin.payment.processor.model.PaymentRequest;
import prac.fin.payment.processor.model.PaymentResult;
import prac.fin.payment.processor.model.RefundRequest;
import prac.fin.payment.processor.model.SettlementRecord;

/**
 * Orchestrates payment processing:
 *   1. Creates a Transaction row (audit trail starts before processor call)
 *   2. Routes to the correct processor via ProcessorRouter
 *   3. Updates the Transaction with the result
 *   4. Returns PaymentResult to the caller (api module)
 *
 * This service does NOT know about Razorpay specifically.
 * It only knows the PaymentProcessor interface.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessorService {

	@Value("${app.processor.default:razorpay}")
    private String defaultProcessor;
	
	private final ProcessorRouter processorRouter;
    private final TransactionRepository transactionRepository;
    
    /**
     * Initiates a charge using decrypted card data from our Card Vault.
     *
     * The request already contains the decrypted card number, expiry, and CVV.
     * These come from the Card Vault service and decrypted just-in-time, held
     * in memory only for the duration of this method call.
     *
     * Transaction row is created BEFORE the processor call.
     * If we crash after calling the processor but before saving the result,
     * the PENDING row tells the reconciliation job to investigate.
     */
    @Transactional
    public PaymentResult charge(PaymentIntent intent, PaymentRequest request) {
    	Transaction transaction = Transaction.builder()
    			.paymentIntent(intent)
    			.processor(defaultProcessor)
    			.amount(intent.getAmount())
    			.currency(intent.getCurrency())
    			.build();
    	transactionRepository.save(transaction);
    	
    	PaymentRequest requestWithTxnId = getPaymentRequestWithTxnId(request, transaction.getId().toString());
    	
    	PaymentResult result = null;
    	try {
    		result = processorRouter.route(defaultProcessor).charge(requestWithTxnId);
    	} catch (ProcessorException e) {
    		// Processor itself is unavailable. Not a bank decline
            // Mark as PENDING_VERIFICATION, not FAILED as money may have moved
            transaction.setStatus(TransactionStatus.PENDING_VERIFICATION);
            transaction.setErrorMessage(e.getMessage());
            transactionRepository.save(transaction);
            throw e;
    	}
    	
    	transaction.setStatus(result.getStatus());
        transaction.setProcessorTxnId(result.getProcessorPaymentId());
        transaction.setErrorCode(result.getErrorCode());
        transaction.setErrorMessage(result.getErrorMessage());
        transactionRepository.save(transaction);
    	
        log.info("Charge complete: intentId={} status={} processorPaymentId={}",
                intent.getId(), result.getStatus(), result.getProcessorPaymentId());
        
    	return result;
    }
    
    /**
     * Issues a refund against a previously captured transaction.
     * Creates a new Transaction row for the refund.
     */
    @Transactional
    public PaymentResult refund(Transaction originalTransaction, Long amountToRefund) {
    	Transaction refundTransaction = Transaction.builder()
                .paymentIntent(originalTransaction.getPaymentIntent())
                .processor(originalTransaction.getProcessor())
                .amount(amountToRefund)
                .currency(originalTransaction.getCurrency())
                .status(TransactionStatus.PENDING)
                .parentTransaction(originalTransaction)
                .build();
 
        transactionRepository.save(refundTransaction);
        
        RefundRequest request = RefundRequest.builder()
                .processorPaymentId(originalTransaction.getProcessorTxnId())
                .amount(amountToRefund)
                .currency(originalTransaction.getCurrency())
                .transactionId(refundTransaction.getId().toString())
                .build();
        
        PaymentProcessor processor = processorRouter.route(originalTransaction.getProcessor());
        PaymentResult result = processor.refund(request);
 
        refundTransaction.setStatus(result.getStatus());
        refundTransaction.setProcessorTxnId(result.getProcessorPaymentId());
        transactionRepository.save(refundTransaction);
 
        // Mark the original transaction as refunded
        originalTransaction.setStatus(TransactionStatus.REFUNDED);
        transactionRepository.save(originalTransaction);
 
        return result;
    }
    
    private PaymentRequest getPaymentRequestWithTxnId(PaymentRequest request, String txnId) {
    	return PaymentRequest.builder()
                .intentId(request.getIntentId())
                .transactionId(txnId)
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .paymentMethodType(request.getPaymentMethodType())
                .cardNumber(request.getCardNumber())
                .expiryMonth(request.getExpiryMonth())
                .expiryYear(request.getExpiryYear())
                .cvv(request.getCvv())
                .cardHolderName(request.getCardHolderName())
                .upiId(request.getUpiId())
                .build();
    }
    
    /**
     * Checks a payment's status directly with the processor.
     * Called by the reconciliation service for PENDING_VERIFICATION rows.
     */
    public PaymentResult checkStatus(Transaction transaction) {
    	PaymentProcessor processor = processorRouter.route(transaction.getProcessor());
        return processor.checkStatus(transaction.getProcessorTxnId());
    }
    
    public List<SettlementRecord> downloadSettlementFile(String processor, LocalDate settlementDate) {
    	return processorRouter.route(processor).downloadSettlementFile(settlementDate);
    }
}
