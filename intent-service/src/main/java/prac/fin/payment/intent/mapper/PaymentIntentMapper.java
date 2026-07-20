package prac.fin.payment.intent.mapper;

import org.springframework.stereotype.Component;

import prac.fin.payment.common.dto.PaymentIntentResponse;
import prac.fin.payment.domain.entity.PaymentIntent;

/**
 * Converts between PaymentIntent entity and PaymentIntentResponse DTO.
 */
@Component
public class PaymentIntentMapper {

	private static final String INTENT_ID_PREFIX = "pi_";
	
	public PaymentIntentResponse toResponse(PaymentIntent intent) {
		return PaymentIntentResponse.builder()
				.id(INTENT_ID_PREFIX + intent.getId().toString())
				.amount(intent.getAmount())
                .currency(intent.getCurrency())
                .status(intent.getStatus())
                .clientSecret(intent.getClientSecret())
                .paymentMethodType(intent.getPaymentMethodType())
                .metadata(intent.getMetadata())
                .createdAt(intent.getCreatedAt())
                .updatedAt(intent.getUpdatedAt())
                .build();
	}
	
	public String extractId(String prefixedId) {
        if (prefixedId == null || !prefixedId.startsWith(INTENT_ID_PREFIX)) {
            throw new IllegalArgumentException(
                "Invalid intent ID format: " + prefixedId
            );
        }
        return prefixedId.substring(INTENT_ID_PREFIX.length());
    }
}
