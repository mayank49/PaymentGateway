package prac.fin.payment.session.mapper;

import org.springframework.stereotype.Component;

import prac.fin.payment.common.dto.SessionResponse;
import prac.fin.payment.session.model.CheckoutSession;

@Component
public class SessionMapper {

	private static final String CHECKOUT_BASE_URL = "https://mayankpay.gateway.com/checkout/";
	
	public SessionResponse toResponse(CheckoutSession session) {
        return SessionResponse.builder()
                .sessionId(session.getSessionId())
                .intentId(session.getIntentId())
                .checkoutUrl(CHECKOUT_BASE_URL + session.getSessionId())
                .successUrl(session.getSuccessUrl())
                .cancelUrl(session.getCancelUrl())
                .expiresAt(session.getExpiresAt())
                .build();
    }
}
