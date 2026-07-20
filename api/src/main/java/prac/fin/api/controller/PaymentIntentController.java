package prac.fin.api.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import prac.fin.payment.common.dto.CreatePaymentIntentRequest;
import prac.fin.payment.common.dto.PaymentIntentResponse;
import prac.fin.payment.domain.entity.Merchant;
import prac.fin.payment.intent.service.PaymentIntentService;

@RestController
@RequestMapping("/v1/payment-intents")
@RequiredArgsConstructor
public class PaymentIntentController extends BaseController {

	private final PaymentIntentService intentService;
	
	/**
	 * POST /v1/payment-intents
	 * 
	 * @param request
	 * @param httpRequest
	 * @return {@link PaymentIntentResponse}
	 */
	@PostMapping
	public ResponseEntity<PaymentIntentResponse> create(
			@Valid @RequestBody CreatePaymentIntentRequest request,
			HttpServletRequest httpRequest) {
		Merchant merchant = getMerchant(httpRequest);
		PaymentIntentResponse response = intentService.create(request, merchant);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}
	
	/**
	 * GET /v1/payment-intents/{id}
	 * 
	 * @param id
	 * @param httpRequest
	 * @return {@link PaymentIntentResponse}
	 */
	@GetMapping("/{id}")
	public ResponseEntity<PaymentIntentResponse> getById(
			@PathVariable String id,
			HttpServletRequest httpRequest) {
		Merchant merchant = getMerchant(httpRequest);
		return ResponseEntity.ok(intentService.getById(id, merchant));
	}
	
	/**
	 * GET /v1/payment-intents?page=0&size=20
	 * 
	 * @param page
	 * @param size
	 * @param httpRequest
	 * @return List of {@link PaymentIntentResponse}
	 */
	@GetMapping
	public ResponseEntity<Page<PaymentIntentResponse>> list(
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size,
			HttpServletRequest httpRequest) {
		Merchant merchant = getMerchant(httpRequest);
		Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
		return ResponseEntity.ok(intentService.listByMerchant(merchant, pageable));
	}
	
	/**
	 * POST /v1/payment-intents/{id}/cancel
	 * 
	 * @param id
	 * @param httpRequest
	 * @return {@link PaymentIntentResponse}
	 */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<PaymentIntentResponse> cancel(
            @PathVariable String id,
            HttpServletRequest httpRequest) {
 
    	Merchant merchant = getMerchant(httpRequest);
        return ResponseEntity.ok(intentService.cancel(id, merchant));
    }
}
