package prac.fin.payment.processor.adapter;

import java.time.LocalDate;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import com.razorpay.Payment;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Refund;
import com.razorpay.Settlement;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import prac.fin.payment.common.enums.PaymentMethodType;
import prac.fin.payment.common.exception.ProcessorException;
import prac.fin.payment.processor.model.PaymentRequest;
import prac.fin.payment.processor.model.PaymentResult;
import prac.fin.payment.processor.model.RefundRequest;
import prac.fin.payment.processor.model.SettlementRecord;
import prac.fin.payment.processor.service.PaymentProcessor;

/**
 * Razorpay implementation of PaymentProcessor.
 * 
 * ------------------------------------------------------------------------
 * IMPORTANT: PCI DSS REQUIREMENT
 * 
 * This adapter uses Razorpay's direct S2S API (createJsonPayment).
 * Your platform MUST be PCI DSS certified and explicitly enabled
 * by Razorpay before this will work in production.
 * Standard Razorpay accounts use their hosted checkout (Razorpay.js).
 * Contact Razorpay support to enable S2S access on your account.
 * 
 * -----------------------------------------------------------------------
 * 
 * S2S FLOW:
 *   1. We collect card details on our own checkout page
 *   2. We POST them directly to Razorpay via createJsonPayment
 *   3. For 3DS payments, Razorpay returns a next[] array with OTP URL
 *   4. We redirect the customer to that URL for OTP
 *   5. Bank posts back to our callback URL after OTP
 *   6. We verify the payment status
 *
 * API endpoint: POST https://api.razorpay.com/v1/payments/create/json
 * 
 * Docs : 
 * 	https://razorpay.com/docs/payments/payment-gateway/s2s-integration/
 * 	https://razorpay.com/docs/payments/payment-gateway/s2s-integration/json/v1/
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RazorpayProcessorAdapter implements PaymentProcessor {

	private static final String PROCESSOR_NAME = "razorpay";
	
	private final RazorpayClient razorpayClient;
	
	@Override
	public String getName() {
		return PROCESSOR_NAME;
	}

	/**
     * Initiates a card charge via Razorpay's S2S API.
     *
     * The response is NOT always synchronous. For 3DS cards:
     *   - Response contains next[]
     *   	[
     *     	  {
     *           "action": "redirect",
     *       	 "url": "https://api.razorpay.com/v1/payments/pay_xxx/authorize"
     *        }
     *   	]
     *   - We return REQUIRES_ACTION so the caller can redirect the customer
     *   - After OTP, bank posts to our callback URL
     *   - We then call checkStatus() to confirm the outcome
     *
     * For non-3DS cards (rare in India), the response is synchronous.
     */
	@Override
	public PaymentResult charge(PaymentRequest request) {
		try {
			JSONObject payload = buildChargePayload(request);
			
			log.info("Initiating Razorpay S2S charge: intentId={} amount={} currency={}",
	                    request.getIntentId(), request.getAmount(), request.getCurrency());
			
			Payment payment = razorpayClient.payments.createJsonPayment(payload);
			String paymentId = payment.get("razorpay_payment_id");
			
			// next[] -> bank requires OTP / 3DS action
			if(payment.has("next")) {
				JSONArray nextArray = payment.toJson().getJSONArray("next");
				if(nextArray != null) {
					JSONObject nextAction = nextArray.getJSONObject(0);
					//action = "redirect", url = the bank's OTP/3DS page
					String redirectUrl = nextAction.getString("url");
					log.info("Razorpay 3DS required: action={} paymentId={}",
                            nextAction.getString("action"), paymentId);
					return PaymentResult.requiresAction(paymentId, redirectUrl);
				}
			}
			
			 // No next[] -> non-3DS synchronous authorization
			log.info("Razorpay synchronous capture: paymentId={}", paymentId);
			return PaymentResult.captured(paymentId);
			
		} catch (RazorpayException e) {
			return handleException(e, request.getIntentId());
		}
	}

	@Override
	public PaymentResult refund(RefundRequest request) {
		try {
			JSONObject data = new JSONObject();
			data.put("amount", request.getAmount());
			data.put("notes", new JSONObject()
					.put("transaction_id", request.getTransactionId()));
			
			
			Refund refund = razorpayClient.payments.refund(request.getProcessorPaymentId(), data);
			
			return PaymentResult.refunded(refund.get("id"));
		} catch(RazorpayException e) {
			return handleException(e, request.getProcessorPaymentId());
		}
	}

	@Override
	public PaymentResult checkStatus(String processorPaymentId) {
		try {
            Payment payment = razorpayClient.payments.fetch(processorPaymentId);
            String status = payment.get("status");

            return switch (status) {
                case "captured", "authorized" -> PaymentResult.captured(processorPaymentId);
                case "refunded"               -> PaymentResult.refunded(processorPaymentId);
                case "failed"                 -> PaymentResult.failed(
                        payment.get("error_code"),
                        payment.get("error_description"));
                default                       -> PaymentResult.pendingVerification();
            };

        } catch (RazorpayException e) {
            return handleException(e, processorPaymentId);
        }
	}
	
	private JSONObject buildChargePayload(PaymentRequest request) {
		JSONObject data = new JSONObject();
		data.put("amount", request.getAmount());
		data.put("currency", request.getCurrency());
		data.put("email", request.getEmail());
		data.put("contact", request.getPhone());
		data.put("order_id", request.getProcessorOrderRef()); // pre-created Razorpay order ID
		
		if(request.getPaymentMethodType() == PaymentMethodType.CARD) {
			data.put("method", "card");
			data.put("card[number]", request.getCardNumber());
			data.put("card[name]", request.getCardHolderName());
			data.put("card[expiry_month]", request.getExpiryMonth());
			data.put("card[expiry_year]",  request.getExpiryYear());
            data.put("card[cvv]",          request.getCvv());
            
            // 3DS 2.0 browser fingerprint required by Razorpay S2S
            // These come from the customer's browser via JS on our checkout page
            if (request.getBrowserInfo() != null) {
                var browser = request.getBrowserInfo();
                data.put("browser",              new JSONObject()
	                    .put("java_enabled",        browser.isJavaEnabled())
	                    .put("javascript_enabled",  browser.isJavascriptEnabled())
	                    .put("timezone_offset",     browser.getTimezoneOffset())
	                    .put("color_depth",         browser.getColorDepth())
	                    .put("screen_width",        browser.getScreenWidth())
	                    .put("screen_height",       browser.getScreenHeight()));
            }
            data.put("authentication_channel", "browser");
		} else if (request.getPaymentMethodType() == PaymentMethodType.UPI) {
            data.put("method", "upi");
            data.put("vpa",    request.getUpiId());
        }
		
		return data;
	}
	
	/**
     * BAD_REQUEST_ERROR = bank hard decline (wrong CVV, insufficient funds etc.)
     *   → definitive FAILED, no money moved
     *
     * GATEWAY_ERROR / SERVER_ERROR = infrastructure issue
     *   → PENDING_VERIFICATION, money may or may not have moved
     */
    private PaymentResult handleException(RazorpayException e, String ref) {
        log.error("Razorpay exception for {}: {}", ref, e.getMessage());
        if (e.getMessage() != null && e.getMessage().contains("BAD_REQUEST_ERROR")) {
            return PaymentResult.failed("BAD_REQUEST_ERROR", e.getMessage());
        }
        return PaymentResult.pendingVerification();
    }

    /**
     * Downloads Razorpay's combined settlement report for a given date
     * and normalises it into SettlementRecords
     * 
     * API: GET https://api.razorpay.com/v1/settlements/recon/combined
     * ?year={yyyy}&month={MM}&day={dd}&count=1000
     * 
     * Ref: 
     *   - https://razorpay.com/docs/api/settlements/
     *   - https://github.com/razorpay/razorpay-java/blob/master/documents/settlement.md
     * 
     */
	@Override
	public List<SettlementRecord> downloadSettlementFile(LocalDate date) {
		try {
			JSONObject params = new JSONObject();
            params.put("year",  date.getYear());
            params.put("month", date.getMonthValue());
            params.put("day",   date.getDayOfMonth());
 
            List<Settlement> settlements =
                    razorpayClient.settlement.reports(params);
 
            return settlements.stream()
                    .filter(s -> "payment".equals(s.get("type"))) // only payment rows, not refunds/adjustments
                    .map(s -> new SettlementRecord(
                            s.get("entity_id"),     // payment_id e.g. pay_xxx
                            ((Number) s.get("amount")).longValue(), // already in paise
                            s.get("type"),
                            date
                    ))
                    .toList();
		} catch (RazorpayException e) {
            throw new ProcessorException(
                    PROCESSOR_NAME, "Failed to download settlement file", e);
        }
	}
}
