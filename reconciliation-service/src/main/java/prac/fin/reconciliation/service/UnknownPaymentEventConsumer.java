package prac.fin.reconciliation.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import prac.fin.payment.common.dto.event.PaymentEventMessage;
import prac.fin.payment.common.enums.IntentStatus;
import prac.fin.payment.common.enums.ReconciliationStatus;
import prac.fin.payment.common.enums.TransactionStatus;
import prac.fin.payment.domain.entity.MerchantLedger;
import prac.fin.payment.domain.entity.PaymentIntent;
import prac.fin.payment.domain.entity.Transaction;
import prac.fin.payment.domain.repository.MerchantLedgerRepository;
import prac.fin.payment.domain.repository.PaymentIntentRepository;
import prac.fin.payment.domain.repository.TransactionRepository;
import prac.fin.payment.processor.model.PaymentResult;
import prac.fin.payment.processor.service.ProcessorService;

@Slf4j
@Service
@RequiredArgsConstructor
public class UnknownPaymentEventConsumer {
	
	private final ProcessorService processorService;
	private final PaymentIntentRepository intentRepository;
	private final TransactionRepository transactionRepository;
	private final MerchantLedgerRepository ledgerRepository;
	private final KafkaTemplate<String, PaymentEventMessage> reconciliationKafkaTemplate;

	@KafkaListener(
		topics = PaymentEventMessage.TOPIC_UNKNOWN,
		containerFactory = "reconciliationListenerFactory"	
	)
	public void onUnknownPaymentEvent(@Payload PaymentEventMessage event,
		Acknowledgment acknowledgment) {
		log.info("Received payment.unknown event: intentId={}", event.getIntentId());
		
		String intentIdStr = event.getIntentId().substring("pi_".length());
		Optional<PaymentIntent> intent = intentRepository.findById(UUID.fromString(intentIdStr));
		
		if(intent.isEmpty()) {
			log.error("Intent not found for unknown payment event: {}", event.getIntentId());
            acknowledgment.acknowledge();
            return;
		}
		
		//Find the most recent PENDING_VERIFICATION transaction for this intent
		Optional<Transaction> transaction = transactionRepository
				.findTopByIntentIdAndStatusOrderByCreatedAtDesc(intent.get().getId(), TransactionStatus.PENDING_VERIFICATION);
		
		if(transaction.isEmpty()) {
			log.warn("No PENDING_VERIFICATION transaction found for intentId={}",
                    event.getIntentId());
            acknowledgment.acknowledge();
            return;
		}
		
		PaymentResult result = processorService.checkStatus(transaction.get());
		processResult(result, intent.get(), transaction.get());
	}
	
	private void processResult(PaymentResult result, PaymentIntent intent, Transaction transaction) {
		switch(result.getStatus()) {
			case CAPTURED -> 
				processCapturedStatus(result, intent, transaction);
			case FAILED ->
				processFailedStatus(result, intent, transaction);
			default -> {
				 log.warn("Unknown payment still unresolved after checkStatus: intentId={}",
						 intent);
				 writeLedgerRow(transaction, null, null, ReconciliationStatus.UNMATCHED);
			}
		}
	}
	
	private void processCapturedStatus(PaymentResult result, PaymentIntent intent, Transaction transaction) {
		log.info("Unknown payment resolved as CAPTURED: intentId={} processorPaymentId={}",
				intent.getId(), result.getProcessorPaymentId());
		
		transaction.setStatus(TransactionStatus.CAPTURED);
		transaction.setProcessorTxnId(result.getProcessorPaymentId());
		transactionRepository.save(transaction);
		
		intentRepository.updateStatusIfEquals(intent.getId(), IntentStatus.UNKNOWN, IntentStatus.SUCCEEDED);
		writeLedgerRow(transaction, result.getProcessorPaymentId(), transaction.getAmount(), ReconciliationStatus.MATCHED);
		publishResolved(intent, result, PaymentEventMessage.TOPIC_SUCCEEDED);
	}
	
	private void processFailedStatus(PaymentResult result, PaymentIntent intent, Transaction transaction) {
		log.info("Unknown payment resolved as FAILED: intentId={}", intent.getId());
		 
        transaction.setStatus(TransactionStatus.FAILED);
        transaction.setErrorCode(result.getErrorCode());
        transactionRepository.save(transaction);

        intentRepository.updateStatusIfEquals(
        		intent.getId(), IntentStatus.UNKNOWN, IntentStatus.FAILED);
        publishResolved(intent, result, PaymentEventMessage.TOPIC_FAILED);
	}
	
	private void writeLedgerRow(Transaction transaction, String processorTxnId, Long processorAmount, ReconciliationStatus status) {
		if (ledgerRepository.existsByTransactionId(transaction.getId())) {
            return;
        }
		ledgerRepository.save(MerchantLedger.builder()
                .merchant(transaction.getPaymentIntent().getMerchant())
                .transaction(transaction)
                .processorTxnId(processorTxnId)
                .internalAmount(transaction.getAmount())
                .processorAmount(processorAmount)
                .status(status)
                .settlementDate(LocalDate.now())
                .processorId(transaction.getProcessor())
                .build());
	}
	
	 private void publishResolved(PaymentIntent intent, PaymentResult result, String topic) {
		 PaymentEventMessage message = PaymentEventMessage.builder()
	                .eventType(topic)
	                .intentId("pi_" + intent.getId())
	                .merchantId(intent.getMerchant().getId().toString())
	                .amount(intent.getAmount())
	                .currency(intent.getCurrency())
	                .status(intent.getStatus())
	                .paymentMethodType(intent.getPaymentMethodType())
	                .processorPaymentId(result.getProcessorPaymentId())
	                .metadata(intent.getMetadata())
	                .merchantWebhookUrl(intent.getMerchant().getWebhookUrl())
	                .occurredAt(Instant.now())
	                .build();
		 
		 reconciliationKafkaTemplate.send(topic, intent.getId().toString(), message)
         	.whenComplete((res, ex) -> {
             if (ex != null) {
                 log.error("Failed to publish {} for intentId={}: {}",
                         topic, intent.getId(), ex.getMessage());
             }
         });
	 }
}
