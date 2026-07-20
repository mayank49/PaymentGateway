package prac.fin.webhook.service;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import prac.fin.webhook.service.dto.WebhookDeliveryStatus;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookDeliveryService {
	
	private static final String HMAC_ALGORITHM = "HmacSHA256";
	private static final String SIGNATURE_HEADER = "X-Webhook-Signature";
	private static final String EVENT_TYPE_HEADER = "X-Webhook-Event";
	private static final String EVENT_ATTEMPT_HEADER = "X-Webhook-Attempt";
	
	private final RestTemplate restTemplate;

	public WebhookDeliveryStatus deliver(String webhookUrl, String payloadJson,
            String merchantId, String eventType, int attemptCount) {
		try {
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			headers.set(SIGNATURE_HEADER, computeSignature(payloadJson, merchantId));
			headers.set(EVENT_TYPE_HEADER, eventType);
			headers.set(EVENT_ATTEMPT_HEADER, String.valueOf(attemptCount));
			
			HttpEntity<String> entity = new HttpEntity<>(payloadJson, headers);
			
			ResponseEntity<String> response = 
					restTemplate.postForEntity(webhookUrl, entity, String.class);
			
			WebhookDeliveryStatus status = WebhookDeliveryStatus.builder()
					.delivered(response.getStatusCode().is2xxSuccessful())
					.statusCode(response.getStatusCode().value()).build();
			return status;
		} catch (Exception e) {
            log.warn("HTTP POST to merchant webhook failed: url={} error={}",
                    webhookUrl, e.getMessage());
            return WebhookDeliveryStatus.builder()
					.delivered(false).build();
        }
	}
	
	private String computeSignature(String payload, String merchantId) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(
                    merchantId.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(
                    mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Signature computation failed", e);
        }
    }
}
