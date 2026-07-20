package prac.fin.payment.intent.service;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import prac.fin.payment.common.dto.CreatePaymentIntentRequest;
import prac.fin.payment.common.dto.PaymentIntentResponse;
import prac.fin.payment.common.enums.IntentStatus;
import prac.fin.payment.common.exception.InvalidRequestException;
import prac.fin.payment.common.exception.InvalidStateTransitionException;
import prac.fin.payment.common.exception.ResourceNotFoundException;
import prac.fin.payment.domain.entity.Merchant;
import prac.fin.payment.domain.entity.PaymentIntent;
import prac.fin.payment.domain.repository.PaymentIntentRepository;
import prac.fin.payment.intent.mapper.PaymentIntentMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentIntentService {
	
	private static final Set<String> SUPPORTED_CURRENCIES = Set.of(
            "INR", "USD", "EUR", "GBP", "SGD", "AED"
    );
	
	private final PaymentIntentRepository intentRepository;
    private final PaymentIntentMapper mapper;
    
    /**
     * Creates a new PaymentIntent or returns an existing one if the
     * idempotency key was already used.
     *
     * @Transactional ensures the idempotency check and the save
     * happen in the same database transaction. Without this, two
     * concurrent requests with the same key could both pass the check
     * and both try to insert, causing a unique constraint violation.
     * The transaction + unique constraint together make this safe:
     * one will succeed, one will hit the constraint and fail gracefully.
     */
    @Transactional
    public PaymentIntentResponse create(CreatePaymentIntentRequest request, Merchant merchant) {
    	Optional<PaymentIntent> existing = intentRepository.findByIdempotencyKey(request.getIdempotencyKey());
    	if(existing.isPresent()) {
    		log.info("Idempotency hit for key={} intentId={}",
                    request.getIdempotencyKey(), existing.get().getId());
    		return mapper.toResponse(existing.get());
    	}
    	if (!SUPPORTED_CURRENCIES.contains(request.getCurrency())) {
            throw new InvalidRequestException(
                    "currency",
                    "Unsupported currency: " + request.getCurrency()
                    + ". Supported: " + SUPPORTED_CURRENCIES
            );
        }
    	PaymentIntent intent = PaymentIntent.builder()
	        .merchant(merchant)
	        .amount(request.getAmount())
	        .currency(request.getCurrency())
	        .idempotencyKey(request.getIdempotencyKey())
	        .clientSecret(generateClientSecret())
	        .paymentMethodType(request.getPaymentMethodType())
	        .metadata(request.getMetadata() != null ? request.getMetadata() : new java.util.HashMap<>())
	        .build();
    	PaymentIntent saved = intentRepository.save(intent);
    	log.info("Created PaymentIntent id={} merchantId={} amount={} currency={}",
                saved.getId(), merchant.getId(), saved.getAmount(), saved.getCurrency());
    	return mapper.toResponse(saved);
    }
    
    /**
     * Fetches a single intent and verifies it belongs to the requesting merchant.
     *
     * @Transactional(readOnly = true) tells JPA this is a read-only operation.
     * Hibernate skips dirty checking (comparing entity state before/after)
     * which makes reads meaningfully faster.
     */
    @Transactional(readOnly = true)
    public PaymentIntentResponse getById(String prefixedId, Merchant merchant) {
        var intent = findAndVerifyOwnership(prefixedId, merchant);
        return mapper.toResponse(intent);
    }
    
    /**
     * Returns a paginated list of intents for the merchant's dashboard.
     * Never returns all intents, always paginated.
     */
    @Transactional(readOnly = true)
    public Page<PaymentIntentResponse> listByMerchant(
            Merchant merchant,
            Pageable pageable) {

        return intentRepository
                .findByMerchantId(merchant.getId(), pageable)
                .map(mapper::toResponse);
    }
    
    /**
     * Cancels an intent. Only allowed if it hasn't reached PROCESSING yet.
     * Once money is in flight, you can't cancel, you can only refund after.
     */
    @Transactional
    public PaymentIntentResponse cancel(String prefixedId, Merchant merchant) {
        PaymentIntent intent = findAndVerifyOwnership(prefixedId, merchant);
        
        if (!intent.getStatus().canTransitionTo(IntentStatus.CANCELED)) {
            throw new InvalidStateTransitionException(
                    prefixedId,
                    intent.getStatus(),
                    IntentStatus.CANCELED
            );
        }

        int updated = intentRepository.updateStatusIfEquals(
                intent.getId(),
                intent.getStatus(),        // expected current status
                IntentStatus.CANCELED      // new status
        );

        if (updated == 0) {
            // Another thread changed the status between our read and this update.
            // Re-fetch and return the current state.
            log.warn("Concurrent status change detected for intent={}", prefixedId);
            return getById(prefixedId, merchant);
        }

        intent.setStatus(IntentStatus.CANCELED);
        return mapper.toResponse(intent);
    }
    
    
    /**
     * Generates the client secret. A short-lived token for the merchant's frontend.
     * Format: cs_<UUID>
     *
     * UUID.randomUUID() is cryptographically random (uses SecureRandom internally).
     * Safe to use as a secret token without additional entropy.
     */
    private String generateClientSecret() {
        return "cs_" + UUID.randomUUID().toString().replace("-", "");
    }
    
    private PaymentIntent findAndVerifyOwnership(String prefixedId, Merchant merchant) {
        UUID id;
        try {
            id = UUID.fromString(mapper.extractId(prefixedId));
        } catch (IllegalArgumentException e) {
            throw new ResourceNotFoundException("PaymentIntent", prefixedId);
        }

        PaymentIntent intent = intentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PaymentIntent", prefixedId));

        if (!intent.getMerchant().getId().equals(merchant.getId())) {
            throw new ResourceNotFoundException("PaymentIntent", prefixedId);
        }

        return intent;
    }
    
    public PaymentIntent getByPrefixedId(String prefixedId) {
    	UUID id;
        try {
            id = UUID.fromString(mapper.extractId(prefixedId));
        } catch (IllegalArgumentException e) {
            throw new ResourceNotFoundException("PaymentIntent", prefixedId);
        }
        
        PaymentIntent intent = intentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PaymentIntent", prefixedId));
        return intent;
    }
    
    public boolean transitionStatus(UUID intentId, IntentStatus from, IntentStatus to) {
    	PaymentIntent intent = intentRepository.findById(intentId)
                .orElseThrow(() -> new ResourceNotFoundException("PaymentIntent", intentId.toString()));
    	if(intent.getStatus() != from || !intent.getStatus().canTransitionTo(to)) {
    		return false;
    	}
    	intent.setStatus(to);
    	intentRepository.save(intent);
    	return true;
    }
}
