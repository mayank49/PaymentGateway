package prac.fin.payment.processor.adapter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.stripe.exception.CardException;
import com.stripe.exception.StripeException;
import com.stripe.model.BalanceTransaction;
import com.stripe.model.BalanceTransactionCollection;
import com.stripe.model.Charge;
import com.stripe.model.PaymentIntent;
import com.stripe.model.PaymentMethod;
import com.stripe.model.Refund;
import com.stripe.param.BalanceTransactionListParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.PaymentMethodCreateParams;
import com.stripe.param.RefundCreateParams;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import prac.fin.payment.common.enums.PaymentMethodType;
import prac.fin.payment.common.exception.ProcessorException;
import prac.fin.payment.processor.config.StripeProperties;
import prac.fin.payment.processor.model.PaymentRequest;
import prac.fin.payment.processor.model.PaymentResult;
import prac.fin.payment.processor.model.RefundRequest;
import prac.fin.payment.processor.model.SettlementRecord;
import prac.fin.payment.processor.service.PaymentProcessor;

/**
 * Stripe processor adapter.
 * 
 * ------------------------------------------------------------------------
 * 
 * NOTE: Stripe is invite-only in India.
 * This adapter is suitable for:   
 * 	- International payments (USD, EUR, GBP etc.)
 * 	- Non-Indian merchant accounts
 *  - Future use when/if Stripe fully opens India 
 *  
 * ------------------------------------------------------------------------
 * 
 * STRIPE S2S FLOW (cleanest API of the three):
 *   1. Create a PaymentMethod with raw card details
 *   2. Create + confirm a PaymentIntent in ONE call (confirm=true)
 *   3. If 3DS required -> status = requires_action, next_action has redirect URL
 *   4. If succeeded -> status = succeeded, done
 *   5. If declined -> CardException with decline_code
 *
 * Unlike PayU, Stripe's response is synchronous for non-3DS cards.
 * For 3DS cards the final outcome still arrives via webhook.
 *
 * Stripe Java SDK: com.stripe:stripe-java
 * 
 * DOCS:
 * 	https://docs.stripe.com/payments/payment-intents
 *  https://docs.stripe.com/payments/finalize-payments-on-the-server
 *  https://docs.stripe.com/api/payment_intents/create?lang=java
 *  https://docs.stripe.com/api/payment_intents/confirm?lang=java
 *  https://docs.stripe.com/api/payment_intents/object?lang=java
 *  https://stripe.dev/stripe-java/com/stripe/model/PaymentIntent.html
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StripeProcessorAdapter implements PaymentProcessor {
	
	 private static final String PROCESSOR_NAME = "stripe";
	 
	 private final StripeProperties stripeProperties;

	 @Override
	 public String getName() {
		return PROCESSOR_NAME;
	 }

	 /**
	  * Charges a card via Stripe's PaymentIntents API.
	  *
	  * STEP 1: Create a PaymentMethod (wraps the raw card details)
	  * STEP 2: Create + confirm a PaymentIntent in one call
	  *
	  * This is the recommended server-side flow per Stripe docs.
	  * The PaymentMethod is ephemeral here and we don't save it unless
	  * the customer opts into saving their card.
	  */
	 @Override
	 public PaymentResult charge(PaymentRequest request) {
		try {
			PaymentMethodCreateParams pmParams = buildPaymentMethodParams(request);
			PaymentMethod paymentMethod =  PaymentMethod.create(pmParams);
			
			PaymentIntentCreateParams piParams = buildPaymentIntentParams(request, paymentMethod.getId());
			PaymentIntent paymentIntent = PaymentIntent.create(piParams);
			
			return switch (paymentIntent.getStatus()) {
	            case "succeeded" -> {
	                log.info("Stripe payment succeeded: paymentIntentId={} intentId={}",
	                        paymentIntent.getId(), request.getIntentId());
	                yield PaymentResult.captured(paymentIntent.getId());
	            }
	            case "requires_action" -> {
	                // 3DS required. next_action.redirect_to_url.url has the OTP page
	                String redirectUrl = paymentIntent.getNextAction()
	                        .getRedirectToUrl().getUrl();
	                log.info("Stripe 3DS required: paymentIntentId={} redirectUrl={}",
	                        paymentIntent.getId(), redirectUrl);
	                yield PaymentResult.requiresAction(paymentIntent.getId(), redirectUrl);
	            }
	            case "requires_confirmation" -> {
	                // Shouldn't happen with confirm=true, but handle defensively
	                yield PaymentResult.pendingVerification();
	            }
	            default -> {
	                log.warn("Unexpected Stripe status: {} for intentId={}",
	                        paymentIntent.getStatus(), request.getIntentId());
	                yield PaymentResult.pendingVerification();
	            }
	        };
		} catch(CardException  e) {
			// CardException = hard bank decline, no money moved
            // e.getDeclineCode() gives machine-readable reason: "insufficient_funds", "incorrect_cvc" etc.
            log.warn("Stripe card declined: declineCode={} intentId={}",
                    e.getDeclineCode(), request.getIntentId());
            return PaymentResult.failed(e.getDeclineCode(), e.getMessage());
		} catch(StripeException  e) {
			 // Other Stripe errors. infrastructure, rate limit etc.
            log.error("Stripe exception for intentId={}: {}", request.getIntentId(), e.getMessage());
            return PaymentResult.pendingVerification();
		}
	 }

	 @Override
	 public PaymentResult refund(RefundRequest request) {
		 try {
			 RefundCreateParams params = RefundCreateParams.builder()
                    .setPaymentIntent(request.getProcessorPaymentId())
                    .setAmount(request.getAmount())
                    .putMetadata("transaction_id", request.getTransactionId())
                    .build();
			 Refund refund = Refund.create(params);

            return switch (refund.getStatus()) {
                case "succeeded" -> PaymentResult.refunded(refund.getId());
                case "pending"   -> PaymentResult.pendingVerification();
                default          -> PaymentResult.failed("REFUND_FAILED", refund.getFailureReason());
            };
        } catch (StripeException e) {
            log.error("Stripe refund failed: {}", e.getMessage());
            return PaymentResult.failed("REFUND_ERROR", e.getMessage());
        }
	 }

	 @Override
	 public PaymentResult checkStatus(String processorPaymentId) {
		try {
			PaymentIntent paymentIntent = PaymentIntent.retrieve(processorPaymentId);
            return switch (paymentIntent.getStatus()) {
                case "succeeded"                -> PaymentResult.captured(processorPaymentId);
                case "canceled"                 -> PaymentResult.failed("CANCELED", "Payment canceled");
                case "requires_payment_method"  -> PaymentResult.failed("DECLINED", "Payment declined");
                default                         -> PaymentResult.pendingVerification();
            };
        } catch (StripeException e) {
            log.error("Stripe status check failed for {}: {}", processorPaymentId, e.getMessage());
            return PaymentResult.pendingVerification();
        }
	 }
	 
	 private PaymentMethodCreateParams buildPaymentMethodParams(PaymentRequest request) {
		if(request.getPaymentMethodType() == PaymentMethodType.CARD) {
			return PaymentMethodCreateParams.builder()
					.setType(PaymentMethodCreateParams.Type.CARD)
					.setCard(PaymentMethodCreateParams.CardDetails.builder()
							.setNumber(request.getCardNumber())
							.setExpMonth((long) request.getExpiryMonth())
							.setExpYear((long) request.getExpiryYear())
							.setCvc(request.getCvv())
							.build())
					.build();
		}
		throw new UnsupportedOperationException(
                "Stripe adapter: unsupported payment method: " + request.getPaymentMethodType());
	 }
	 
	 private PaymentIntentCreateParams buildPaymentIntentParams(PaymentRequest request, String paymentMethodId) {
		 return PaymentIntentCreateParams.builder()
             .setAmount(request.getAmount())
             .setCurrency(request.getCurrency().toLowerCase())
             .setPaymentMethod(paymentMethodId)
             .setConfirm(true)
             // OFF_SESSION = customer not present, no browser to handle 3DS actions
             // ON_SESSION  = customer is present, 3DS can be handled
             .setConfirmationMethod(PaymentIntentCreateParams.ConfirmationMethod.MANUAL)
             // Our internal reference. visible in Stripe dashboard
             .setDescription("Intent: " + request.getIntentId())
             .putMetadata("intent_id",      request.getIntentId())
             .putMetadata("transaction_id",  request.getTransactionId())
             // Return URL after 3DS redirect
             .setReturnUrl("https://pay.gateway.com/checkout/3ds-callback")
             .build();
	 }

	 @Override
	 public List<SettlementRecord> downloadSettlementFile(LocalDate date) {
		 try {
	            long startEpoch = date.atStartOfDay(java.time.ZoneOffset.UTC).toEpochSecond();
	            long endEpoch   = date.plusDays(1).atStartOfDay(java.time.ZoneOffset.UTC).toEpochSecond();
	 
	            BalanceTransactionListParams params = BalanceTransactionListParams.builder()
	                    .setType("charge") // only successful captures
	                    .setCreated(BalanceTransactionListParams.Created.builder()
	                            .setGte(startEpoch)
	                            .setLt(endEpoch)
	                            .build())
	                    // Expand source so we get the charge object inline
	                    // without making a separate API call per transaction
	                    .addExpand("data.source")
	                    .setLimit(100L)
	                    .build();
	 
	            List<SettlementRecord> records = new ArrayList<SettlementRecord>();
	            BalanceTransactionCollection balanceTxns = BalanceTransaction.list(params);
	 
	            // Stripe SDK supports autopagination
	            for (var txn : balanceTxns.autoPagingIterable()) {
	                var charge = (Charge) txn.getSourceObject();
	                if (charge == null) continue;
	 
	                records.add(new SettlementRecord(
	                        charge.getId(),   // ch_xxx : our processorPaymentId
	                        txn.getAmount(),  // already in paise/cents
	                        txn.getType(),    // "charge"
	                        date
	                ));
	            }
	 
	            return records;
	 
	        } catch (StripeException e) {
	            throw new ProcessorException(
	                    PROCESSOR_NAME, "Failed to download Stripe settlement file", e);
	        }
	 }
}
