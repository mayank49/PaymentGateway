package prac.fin.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import prac.fin.payment.common.dto.CreateSessionRequest;
import prac.fin.payment.common.dto.SessionResponse;
import prac.fin.payment.domain.entity.Merchant;
import prac.fin.payment.session.service.SessionService;

@RestController
@RequestMapping("/v1/payment-sessions")
@RequiredArgsConstructor
public class SessionController extends BaseController {

	private final SessionService sessionService;
	
	/**
     * POST /v1/payment-sessions
     *
     * Creates a checkout session for a given PaymentIntent.
     * Returns a checkoutUrl the merchant should redirect their customer to.
     */
	@PostMapping
	public ResponseEntity<SessionResponse> create(
			@Valid @RequestBody CreateSessionRequest request,
			HttpServletRequest httpRequest) {
		Merchant merchant = getMerchant(httpRequest);
		SessionResponse response = sessionService.create(request, merchant.getId());
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}
}
