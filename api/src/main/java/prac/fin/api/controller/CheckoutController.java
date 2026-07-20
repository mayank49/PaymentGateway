package prac.fin.api.controller;

import java.time.Instant;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import prac.fin.api.dto.CheckoutRequest;
import prac.fin.api.dto.CheckoutResponse;
import prac.fin.api.publisher.KafkaEventPublisher;
import prac.fin.payment.common.dto.ApiErrorResponse;
import prac.fin.payment.common.enums.IntentStatus;
import prac.fin.payment.common.exception.InvalidRequestException;
import prac.fin.payment.common.exception.ResourceNotFoundException;
import prac.fin.payment.domain.entity.PaymentIntent;
import prac.fin.payment.intent.service.PaymentIntentService;
import prac.fin.payment.processor.model.PaymentRequest;
import prac.fin.payment.processor.model.PaymentResult;
import prac.fin.payment.processor.service.ProcessorService;
import prac.fin.payment.session.model.CheckoutSession;
import prac.fin.payment.session.service.SessionService;

/**
 * Handles the customer-facing checkout submission.
 *
 * This controller is NOT authenticated with an API key.
 * It's protected by the session ID instead (only someone with a valid
 * session can submit a payment).
 *
 * FLOW:
 *   POST /v1/checkout/{sessionId}/pay
 *   Body: { cardNumber, expiryMonth, expiryYear, cvv, cardHolderName,
 *           email, phone, browserInfo }
 *
 *   1. Validate session is ACTIVE and not expired
 *   2. Mark session as USED (atomic. prevents double submission)
 *   3. Transition intent to PROCESSING
 *   4. Call ProcessorService.charge() with decrypted card details
 *      NOTE: In a full implementation, card details would first go to
 *      the Card Vault service for tokenization. Here we pass them
 *      directly to the processor (still PCI DSS scope, but correct flow).
 *   5. On CAPTURED   → transition intent to SUCCEEDED, return success
 *   6. On REQUIRES_ACTION → return redirect URL for 3DS OTP
 *   7. On FAILED     → transition intent to FAILED, return error
 *   8. On PENDING_VERIFICATION → transition intent to UNKNOWN, return pending
 *   9. Publish outcome event to Kafka for webhook-service to consume and deliver
 */
@Slf4j
@RestController
@RequestMapping("/v1/checkout")
@RequiredArgsConstructor
public class CheckoutController {
	
	private final SessionService sessionService;
	private final PaymentIntentService intentService;
	private final ProcessorService processorService;
	private final KafkaEventPublisher publisher;
	
	@PostMapping("/{sessionId}/pay")
	public ResponseEntity<?> pay(@PathVariable String sessionId,
			@Valid @RequestBody CheckoutRequest request) {
		
		CheckoutSession session = sessionService.getSession(sessionId);
		if (!session.isUsable()) {
            throw new InvalidRequestException("sessionId",
                    "Session is no longer active. Status: " + session.getStatus());
        }
		
		PaymentIntent intent = null;
		try {
			intent = intentService.getByPrefixedId(session.getIntentId());
		} catch(ResourceNotFoundException e) {
			throw new InvalidRequestException("sessionId", "Session references an invalid intent");
		}
		
		sessionService.markSessionUsed(sessionId);
		boolean transitioned = intentService.transitionStatus(intent.getId(), 
				IntentStatus.REQUIRES_PAYMENT_METHOD, IntentStatus.PROCESSING);
		if(!transitioned) {
			 throw new InvalidRequestException("sessionId", "Payment is already being processed");
		} 
		
		PaymentRequest paymentRequest = getPaymentRequest(request, intent, session);
		PaymentResult paymentResult =  processorService.charge(intent, paymentRequest);
		
		return processResponse(session, intent, paymentResult);
	}
	
	private PaymentRequest getPaymentRequest(CheckoutRequest request, PaymentIntent intent, CheckoutSession session) {
		return PaymentRequest.builder()
        .intentId(session.getIntentId())
        .amount(intent.getAmount())
        .currency(intent.getCurrency())
        .paymentMethodType(intent.getPaymentMethodType())
        .cardNumber(request.getCardNumber())
        .expiryMonth(request.getExpiryMonth())
        .expiryYear(request.getExpiryYear())
        .cvv(request.getCvv())
        .cardHolderName(request.getCardHolderName())
        .email(request.getEmail())
        .phone(request.getPhone())
        .upiId(request.getUpiId())
        .processorOrderRef(request.getProcessorOrderRef())
        .browserInfo(request.getBrowserInfo() != null
                ? PaymentRequest.BrowserInfo.builder()
                        .javaEnabled(request.getBrowserInfo().isJavaEnabled())
                        .javascriptEnabled(request.getBrowserInfo().isJavascriptEnabled())
                        .timezoneOffset(request.getBrowserInfo().getTimezoneOffset())
                        .colorDepth(request.getBrowserInfo().getColorDepth())
                        .screenWidth(request.getBrowserInfo().getScreenWidth())
                        .screenHeight(request.getBrowserInfo().getScreenHeight())
                        .userAgent(request.getBrowserInfo().getUserAgent())
                        .language(request.getBrowserInfo().getLanguage())
                        .build()
                : null)
        .build();
	}
	
	private ResponseEntity<?> processResponse(CheckoutSession session, PaymentIntent intent, PaymentResult paymentResult) {
		return switch(paymentResult.getStatus()) {
			case CAPTURED -> {
				intentService.transitionStatus(intent.getId(), IntentStatus.PROCESSING, IntentStatus.SUCCEEDED);
				log.info("Payment succeeded: intentId={} processorPaymentId={}",
                        intent.getId(), paymentResult.getProcessorPaymentId());
				publisher.publishSucceded(intent, paymentResult.getProcessorPaymentId());
				CheckoutResponse response = 
						CheckoutResponse.success(session.getSuccessUrl(), paymentResult.getProcessorPaymentId());
				yield ResponseEntity.ok(response);
			}
			
			case PENDING -> {
				log.info("Payment requires 3DS action: intentId={}", intent.getId());
                intentService.transitionStatus(
                        intent.getId(), IntentStatus.PROCESSING, IntentStatus.REQUIRES_ACTION);
                publisher.publishActionRequired(intent, paymentResult.getRedirectUrl());
                yield ResponseEntity.ok(
                        CheckoutResponse.requiresAction(paymentResult.getRedirectUrl()));
			}
			
			case FAILED -> {
                intentService.transitionStatus(
                        intent.getId(), IntentStatus.PROCESSING, IntentStatus.FAILED);
                log.warn("Payment failed: intentId={} errorCode={}",
                        intent.getId(), paymentResult.getErrorCode());
                publisher.publishFailed(intent);
                yield ResponseEntity.unprocessableEntity().body(
                        ApiErrorResponse.builder()
                                .code("PAYMENT_DECLINED")
                                .message("Payment was declined")
                                .timestamp(Instant.now())
                                .build());
            }
 
            case PENDING_VERIFICATION -> {
                intentService.transitionStatus(
                        intent.getId(), IntentStatus.PROCESSING, IntentStatus.UNKNOWN);
                log.warn("Payment pending verification (processor unreachable): intentId={}",
                        intent.getId());
                publisher.publishUnknown(intent);
                yield ResponseEntity.accepted().body(
                        CheckoutResponse.pending(
                                "Payment is being verified. You will be notified via email."));
            }
			
			default -> {
                log.error("Unexpected processor result status: {} for intentId={}",
                		paymentResult.getStatus(), intent.getId());
                yield ResponseEntity.internalServerError().build();
            }
		};
	}
}
