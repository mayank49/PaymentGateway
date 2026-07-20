package prac.fin.payment.processor.adapter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import prac.fin.payment.common.enums.PaymentMethodType;
import prac.fin.payment.common.exception.ProcessorException;
import prac.fin.payment.processor.config.PayUProperties;
import prac.fin.payment.processor.model.PaymentRequest;
import prac.fin.payment.processor.model.PaymentResult;
import prac.fin.payment.processor.model.RefundRequest;
import prac.fin.payment.processor.model.SettlementRecord;
import prac.fin.payment.processor.service.PaymentProcessor;

/**
 * PayU India v2 S2S processor adapter.
 * 
 * ------------------------------------------------------------------------
 * IMPORTANT: PCI DSS REQUIREMENT
 * PayU S2S requires PCI DSS certification. You must also explicitly
 * request S2S access from PayU — it is not enabled by default.
 * ------------------------------------------------------------------------
 * 
 * USES: PayU v2/payments API (JSON, recommended for new integrations)
 * Ref: https://docs.payu.in/v2/docs/v2-cards-merchant-hosted-integration
 * 
 * KEY DIFFERENCES FROM THE OLD CLASSIC API:
 *   Old: form-encoded POST, hash in request body
 *   v2:  JSON POST, HMAC auth in Authorization header
 * 
 * AUTHORIZATION HEADER FORMAT (from PayU docs):
 *   hmac username="<merchantKey>", algorithm="sha512", headers="date",
 *        signature="SHA512(requestBody|date|merchantSecret)"
 *        
 * AMOUNT: v2 API takes rupees as a decimal (e.g. 250.00), NOT paise.
 *   We receive paise internally. Convert before sending.
 *   
 * CARD EXPIRY: "MM/YYYY" string (e.g. "04/2025"), not separate month/year.
 *   { "result": { "paymentId": "...", "redirectUrl": "...", "authAction": "..." }, "status": "PENDING" }
 *   
 *   Always PENDING initially. Final result via callBackActions URLs.
 *   
 * ENDPOINTS:
 *   Test: https://apitest.payu.in/v2/payments
 *   Prod: https://api.payu.in/v2/payments
 *   Verify: https://test.payu.in/v3/transaction  (test)
 * 
 * RESPONSE:
 *   
 * DOCS:
 * 	https://docs.payu.in/v2/docs/v2-seamless-integration
 * 	https://docs.payu.in/v2/docs/v2-cards-merchant-hosted-integration
 *  https://docs.payu.in/docs/integrate-with-s2s-for-cards-classic-integration
 *  https://docs.payu.in/docs/integrate-with-decoupled-flow-s2s
 *  https://docs.payu.in/docs/upi-collection-s2s
 *  https://docs.payu.in/reference/_payment_server_to_server
 * 	
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PayUProcessorAdapter implements PaymentProcessor {

	private static final String PROCESSOR_NAME = "payu";
	
	/**
	 * Define the custom formatter for strict RFC 1123 compliance
	 * 
	 * EEE: Three-letter abbreviation for the day of the week
	 * dd: Two-digit day of the month (e.g., "05").
	 * MMM: Three-letter abbreviation for the month (e.g., "Mar").
	 * yyyy: Four-digit year (e.g., "2026").
	 * HH:mm:ss: 24-hour time format.
	 * GMT': A hardcoded string literal. Note that this does not set the timezone; 
	 * it only adds the text "GMT" to the output.
	 */
	private static final DateTimeFormatter HTTP_DATE =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'");
	
	private final PayUProperties payUProperties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
	
	@Override
	public String getName() {
		return PROCESSOR_NAME;
	}

	/**
	 * Initiates a card payment via PayU's v2/payments JSON API.
	 * 
	 * RESPONSE HANDLING:
	 *   v2 API always returns status='PENDING' initially
	 *   Use result.redirectUrl to redirect customer to bank OTP / 3DS
	 *   Final outcome arrives via successAction/ failureAction webhook callbacks
	 *   
	 * We return requiresAction with the redirectUrl so the api layer
	 * can redirect the customer and store the paymentId for later matching.
	 * 
	 */
	@Override
	public PaymentResult charge(PaymentRequest request) {
		try {
			Map<String, Object> body = buildChargeBody(request);
			String bodyJson = objectMapper.writeValueAsString(body);
			String date = currentHttpDate();
			HttpHeaders headers = buildAuthHeaders(bodyJson, date);
			HttpEntity<String> httpRequest = new HttpEntity<>(bodyJson, headers);
			
			log.info("Initiating PayU v2 charge: intentId={} amount={} txnId={}",
                    request.getIntentId(), request.getAmount(), request.getTransactionId());
			
			var response = restTemplate.postForObject(payUProperties.getPaymentUrl(), httpRequest, Map.class);
			if(response == null) {
				log.error("PayU returned null response for intentId={}", request.getIntentId());
                return PaymentResult.pendingVerification();
			}
			
			// v2 response: { "result": { "paymentId": "...", "redirectUrl": "..." }, "status": "PENDING" }
            Map<String, Object> result = (Map<String, Object>) response.get("result");
            if (result == null) {
                log.error("PayU response missing result field: {}", response);
                return PaymentResult.pendingVerification();
            }
            
            String paymentId   = (String) result.get("paymentId");
            String redirectUrl = (String) result.get("redirectUrl");
            
            log.info("PayU charge initiated: paymentId={} redirectUrl={}", paymentId, redirectUrl);
            
            // Always requires redirect for OTP as PayU v2 has no synchronous card capture
            return PaymentResult.requiresAction(paymentId, redirectUrl);
			
		} catch (Exception e) {
            log.error("PayU charge failed for intentId={}: {}", request.getIntentId(), e.getMessage());
            // Don't mark as FAILED, we don't know if the request reached PayU
            return PaymentResult.pendingVerification();
        }
	}

	/**
     * Initiates a refund via PayU's v2/payments/{paymentId}/refund API.
     * Ref: https://docs.payu.in/v2/reference/refund_payment
     */
	@Override
	public PaymentResult refund(RefundRequest request) {
		try {
			
			Map<String, Object> body = new LinkedHashMap<>();
			body.put("txnId", request.getTransactionId());
			body.put("amount", toRupees(request.getAmount()));
			
			String bodyJson = objectMapper.writeValueAsString(body);
			String date = currentHttpDate();
			
			HttpHeaders headers = buildAuthHeaders(bodyJson, date);
			HttpEntity<String> httpRequest = new HttpEntity<>(bodyJson, headers);
			
			String url = payUProperties.getPaymentUrl()
                    + "/" + request.getProcessorPaymentId() + "/refund";
			
			restTemplate.postForObject(url, httpRequest, Map.class);
			
			log.info("PayU refund initiated: processorPaymentId={} amount={}",
	                    request.getProcessorPaymentId(), request.getAmount());
			// Refund confirmation also arrives asynchronously
	        return PaymentResult.pendingVerification();
			
		} catch (Exception e) {
            log.error("PayU refund failed: {}", e.getMessage());
            return PaymentResult.failed("REFUND_ERROR", e.getMessage());
        }
	}

	/**
     * Verifies payment status via PayU's v3/transaction verify API.
     * Ref: https://docs.payu.in/reference/verify_payment_api
     *
     * Uses Info-Command header rather than a command field in the body.
     */
	@Override
	public PaymentResult checkStatus(String processorPaymentId) {
		try {
			Map<String, Object> body = new LinkedHashMap<>();
			body.put("txnId", processorPaymentId);
			String bodyJson = objectMapper.writeValueAsString(body);
			String date = currentHttpDate();
			HttpHeaders headers = buildAuthHeaders(bodyJson, date);
			headers.set("Info-Command", "verify_payment");
			HttpEntity<String> httpRequest = new HttpEntity<>(bodyJson, headers);
			
			var response = restTemplate.postForObject(payUProperties.getVerifyUrl(), httpRequest, Map.class);
			if(response == null) {
				return PaymentResult.pendingVerification();
			}
			
			Map<String, Object> txnDetails = (Map<String, Object>) response.get("transaction_details");
			if(txnDetails == null || !txnDetails.containsKey(processorPaymentId)) {
				return PaymentResult.pendingVerification();
			}
			
			Map<String, Object> txn = (Map<String, Object>) response.get(processorPaymentId);
			String status = (String) txn.get("status");
			
			return switch (status) {
            case "success"  -> PaymentResult.captured(processorPaymentId);
            case "failure"  -> PaymentResult.failed("PAYMENT_FAILED", "PayU reported failure");
            case "refunded" -> PaymentResult.refunded(processorPaymentId);
            default         -> PaymentResult.pendingVerification();
        };
			
		} catch(Exception e) {
			log.error("PayU check status failed for {}: {}", processorPaymentId, e.getMessage());
			return PaymentResult.pendingVerification();
		}
	}
	
	 private Map<String, Object> buildChargeBody(PaymentRequest request) {
		 Map<String, Object> body = new LinkedHashMap<>();
		 body.put("accountId", payUProperties.getMerchantKey());
		 body.put("referenceId", request.getTransactionId());
		 
		 if(request.getPaymentMethodType() == PaymentMethodType.CARD) {
			 Map<String, Object> card = new LinkedHashMap<>();
			 card.put("cardnumber", request.getCardNumber());
			 card.put("validThrough", formatExpiry(request.getExpiryMonth(), request.getExpiryYear()));
			 card.put("ownerName", request.getCardHolderName());
			 card.put("cvv", request.getCvv());
			 
			 body.put("paymentMethod", Map.of(
                    "name", "CreditCard",
                    "bankCode", "CC",
                    "paymentCard", card
            ));
		 } else if (request.getPaymentMethodType() == PaymentMethodType.UPI) {
            body.put("paymentMethod", Map.of(
                    "name", "UPI",
                    "bankCode", "UPI",
                    "vpa", request.getUpiId()
            ));
        }
		
		body.put("order", Map.of("productInfo", "Payment for " + request.getIntentId(),
	               "paymentChargeSpecification", Map.of("price", toRupees(request.getAmount()))));
		
		// Customer billing details
        body.put("billingDetails", Map.of(
                "firstName", request.getCardHolderName(),
                "email", request.getEmail(),
                "phone", request.getPhone()
        ));
        
	    // Where PayU POSTs the final payment result
	    body.put("callBackActions", Map.of(
	            "successAction", payUProperties.getSuccessUrl(),
	            "failureAction", payUProperties.getFailureUrl()
	    ));
	    
	    return body;
	 }
	 
	/** Converts our internal paise amount to rupees for PayU v2. */
    private double toRupees(Long amountInPaise) {
        return amountInPaise / 100.0;
    }
	 
	/** Formats card expiry as "MM/YYYY" as required by PayU v2. */
    private String formatExpiry(int month, int year) {
        return String.format("%02d/%04d", month, year);
    }
    
    private String currentHttpDate() {
        return ZonedDateTime.now(ZoneOffset.UTC).format(HTTP_DATE);
    }
    
    /**
     * Builds the Authorization header required by PayU v2.
     *
     * Format (from PayU docs):
     *   hmac username="<key>", algorithm="sha512", headers="date",
     *        signature="SHA512(requestBody|date|merchantSecret)"
     */
    private HttpHeaders buildAuthHeaders(String bodyJson, String date) {
    	 String signatureInput = bodyJson + "|" + date + "|" + payUProperties.getSalt();
    	 String signature      = sha512(signatureInput);
    	 
    	 String authHeader = String.format(
                 "hmac username=\"%s\", algorithm=\"sha512\", headers=\"date\", signature=\"%s\"",
                 payUProperties.getMerchantKey(), signature
         );
    	 
    	 HttpHeaders headers = new HttpHeaders();
         headers.setContentType(MediaType.APPLICATION_JSON);
         headers.set("date", date);
         headers.set("Authorization", authHeader);
         return headers;
    }
    
    private String sha512(String input) {
        try {
            var digest = MessageDigest.getInstance("SHA-512");
            var hash   = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-512 not available", e);
        }
    }

	@Override
	public List<SettlementRecord> downloadSettlementFile(LocalDate date) {
		try {
            String dateStr  = date.toString(); // "yyyy-MM-dd"
            String command  = "get_settlement_details_range";
 
            LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("key",     payUProperties.getMerchantKey());
            params.add("command", command);
            params.add("var1",    dateStr); // start date
            params.add("var2",    dateStr); // end date — same day for daily reconciliation
            params.add("hash",    computeSimpleHash(
                    payUProperties.getMerchantKey(),
                    command,
                    dateStr,
                    payUProperties.getSalt()
            ));
 
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
 
            var response = restTemplate.postForObject(
                    payUProperties.getVerifyUrl(),
                    new HttpEntity<>(params, headers),
                    Map.class
            );
 
            if (response == null || !Integer.valueOf(1).equals(response.get("status"))) {
                log.warn("PayU settlement API returned unexpected response: {}", response);
                return List.of();
            }
 
            // Response structure: { "result": [ { "transaction": [ {...}, {...} ] }, ... ] }
            @SuppressWarnings("unchecked")
            var resultList = (List<Map<String, Object>>) response.get("result");
 
            if (resultList == null) return List.of();
 
            return resultList.stream()
                    .flatMap(settlement -> {
                        @SuppressWarnings("unchecked")
                        var transactions = (List<Map<String, Object>>)
                                settlement.get("transaction");
                        if (transactions == null) return java.util.stream.Stream.empty();
                        return transactions.stream();
                    })
                    .filter(txn -> "capture".equals(txn.get("action")))
                    .map(txn -> new SettlementRecord(
                            String.valueOf(txn.get("payuId")),
                            toPayseFromRupees(String.valueOf(txn.get("transactionAmount"))),
                            String.valueOf(txn.get("action")),
                            date
                    ))
                    .toList();
 
        } catch (Exception e) {
            throw new ProcessorException(
                    PROCESSOR_NAME, "Failed to download PayU settlement file", e);
        }
	}
	
	private Long toPayseFromRupees(String rupees) {
        try {
            return Math.round(Double.parseDouble(rupees) * 100);
        } catch (NumberFormatException e) {
            log.warn("Could not parse PayU settlement amount: {}", rupees);
            return 0L;
        }
    }
	
	private String computeSimpleHash(String key, String command, String var1, String salt) {
        return sha512(key + "|" + command + "|" + var1 + "|" + salt);
    }
}
