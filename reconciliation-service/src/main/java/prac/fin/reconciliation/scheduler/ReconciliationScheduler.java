package prac.fin.reconciliation.scheduler;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import prac.fin.payment.common.enums.IntentStatus;
import prac.fin.payment.common.enums.ReconciliationStatus;
import prac.fin.payment.common.enums.TransactionStatus;
import prac.fin.payment.domain.entity.MerchantLedger;
import prac.fin.payment.domain.entity.Transaction;
import prac.fin.payment.domain.repository.MerchantLedgerRepository;
import prac.fin.payment.domain.repository.PaymentIntentRepository;
import prac.fin.payment.domain.repository.TransactionRepository;
import prac.fin.payment.processor.model.SettlementRecord;
import prac.fin.payment.processor.service.ProcessorService;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReconciliationScheduler {

	@Value("${app.processor.default:razorpay}")
	private String defaultProcessor;
	
	private final ProcessorService processorService;
	private final TransactionRepository transactionRepository;
	private final MerchantLedgerRepository ledgerRepository;
	private final PaymentIntentRepository intentRepository;
	
	/**
	 * Daily reconciliation Job.
	 * 
	 * Runs at 06:00 UTC daily.
	 * 
	 * Matches our transaction records against the
	 * processor settlement files.
	 */
	@Scheduled(cron = "0 0 6 * * *")
	public void runDailyReconciliation() {
		var settlementDate = LocalDate.now().minusDays(1);
        log.info("Starting daily reconciliation: date={} processor={}", settlementDate, defaultProcessor);
        
        try {
        	List<SettlementRecord> settlementRows =
                    processorService.downloadSettlementFile(defaultProcessor, settlementDate);
        	log.info("Downloaded {} rows from {} settlement file",
                    settlementRows.size(), defaultProcessor);
        	
        	
        	Map<String, SettlementRecord> settlementByPaymentId = settlementRows.stream()
                    .collect(Collectors.toMap(
                            SettlementRecord::processorPaymentId,
                            r -> r,
                            // If duplicate payment IDs exist, keep the first
                            (a, b) -> a
                    ));
        	
        	Instant start = settlementDate.atStartOfDay().toInstant(ZoneOffset.UTC);
        	Instant end   = settlementDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        	
        	List<Transaction> transactions =
                    transactionRepository.findPendingVerificationBetween(start, end);
        	
        	log.info("Found {} PENDING_VERIFICATION transactions to reconcile",
                    transactions.size());
        	
        	int matched       = 0;
            int unmatched     = 0;
            int discrepancies = 0;
            
            for(Transaction txn : transactions) {
            	if (ledgerRepository.existsByTransactionId(txn.getId())) {
                    continue;
                }
            	SettlementRecord settlementRow = settlementByPaymentId.get(txn.getProcessorTxnId());
            	if (settlementRow == null) {
                    writeLedgerRow(txn, null, null,
                            ReconciliationStatus.UNMATCHED, settlementDate);
                    unmatched++;
 
                } else if (txn.getAmount().equals(settlementRow.amount())) {
                    writeLedgerRow(txn, settlementRow.processorPaymentId(),
                            settlementRow.amount(), ReconciliationStatus.MATCHED, settlementDate);
 
                    txn.setStatus(TransactionStatus.CAPTURED);
                    transactionRepository.save(txn);
 
                    intentRepository.updateStatusIfEquals(
                            txn.getPaymentIntent().getId(),
                            IntentStatus.UNKNOWN, IntentStatus.SUCCEEDED);
                    matched++;
 
                } else {
                    log.error("DISCREPANCY: txnId={} internalAmount={} processorAmount={}",
                            txn.getId(), txn.getAmount(), settlementRow.amount());
 
                    writeLedgerRow(txn, settlementRow.processorPaymentId(),
                            settlementRow.amount(), ReconciliationStatus.DISCREPANCY, settlementDate);
                    discrepancies++;
                }
            }
        } catch(Exception e) {
        	log.error("Reconciliation job failed for date={}", settlementDate, e);
        }
	}
	
	@Transactional
    protected void writeLedgerRow(Transaction txn,
                                   String processorTxnId,
                                   Long processorAmount,
                                   ReconciliationStatus status,
                                   LocalDate settlementDate) {
        ledgerRepository.save(MerchantLedger.builder()
                .merchant(txn.getPaymentIntent().getMerchant())
                .transaction(txn)
                .processorTxnId(processorTxnId)
                .internalAmount(txn.getAmount())
                .processorAmount(processorAmount)
                .status(status)
                .settlementDate(settlementDate)
                .processorId(defaultProcessor)
                .build());
    }
}
