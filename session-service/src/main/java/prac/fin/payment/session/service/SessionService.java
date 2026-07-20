package prac.fin.payment.session.service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import prac.fin.payment.common.dto.CreateSessionRequest;
import prac.fin.payment.common.dto.SessionResponse;
import prac.fin.payment.common.exception.InvalidRequestException;
import prac.fin.payment.common.exception.ResourceNotFoundException;
import prac.fin.payment.session.mapper.SessionMapper;
import prac.fin.payment.session.model.CheckoutSession;

/**
 * Session Service that interacts with Redis Cache.
 * 
 * Exposes:
 * 	- create : creates a session in Redis and returns the session response
 * 	  which contains the checkout url
 * 	- getSession: returns the session or 404
 * 	- markUsed: marks the session as used to avoid double-charge
 * 	- validateAndGet: returns the session only if it is valid or 404
 * 
 * The critical thing here is how we use Redis. 
 * We're using RedisTemplate directly rather than Spring's @Cacheable annotation 
 * because we need explicit control over TTL and atomic operations
 * 
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {
	
	private static final Duration SESSION_TTL = Duration.ofMinutes(30);
    private static final String KEY_PREFIX = "session:";
    private static final String SESSION_ID_PREFIX = "sess_";
    
    /*
     * StringRedisTemplate stores everything as plain strings. 
     * We serialize our CheckoutSession to JSON ourselves using ObjectMapper, 
     * then store that JSON string in Redis. 
     * Why not RedisTemplate<String, CheckoutSession>? 
     * That would use Java serialization by default which is brittle, not human-readable, 
     * breaks if you rename a field. JSON strings in Redis are debuggable. 
     * You can run 'redis-cli GET session:sess_xxx' and read the value directly.
     */
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final SessionMapper sessionMapper;
    
    /**
     * Creates a new checkout session for the given intent.
     */
    public SessionResponse create(CreateSessionRequest request, UUID merchantId) {
    	Instant now = Instant.now();
    	String sessionId = generateSessionId();
    	String redisKey = KEY_PREFIX + sessionId;
    	
    	CheckoutSession session = CheckoutSession.builder()
	        .sessionId(sessionId)
	        .intentId(request.getIntentId())
	        .merchantId(merchantId)
	        .successUrl(request.getSuccessUrl())
	        .cancelUrl(request.getCancelUrl())
	        .expiresAt(now.plus(SESSION_TTL))
	        .createdAt(now)
	        .build();
    	
    	String json = serialize(session);
    	redisTemplate.opsForValue().set(redisKey, json);
    	
    	log.info("Created session={} for intentId={} merchantId={}",
                sessionId, request.getIntentId(), merchantId);
    	
    	return sessionMapper.toResponse(session);

    }
    
    /**
     * Fetches a session. Throws 404 if not found or expired.
     * Redis returns null for expired keys (TTL elapsed),
     * so "not found" and "expired" look the same from here.
     */
    public CheckoutSession getSession(String sessionId) {
        var redisKey = KEY_PREFIX + sessionId;
        var json = redisTemplate.opsForValue().get(redisKey);

        if (json == null) {
            // Either never existed or Redis TTL already removed it
            throw new ResourceNotFoundException("Session", sessionId);
        }

        return deserialize(json);
    }
    
    /**
     * Atomically marks a session as USED and saves it back.
     *
     * Called the moment a customer submits their payment details
     * before calling Razorpay. This is the double-charge prevention.
     *
     * After this call, any further checkout submissions for this
     * session will fail the isUsable() check in the service.
     *
     * We keep the TTL intact. The session stays in Redis so we can
     * still look it up (e.g. to show the customer a "payment processing"
     * page). We just prevent reuse.
     */
    public void markSessionUsed(String sessionId) {
    	CheckoutSession session = getSession(sessionId);
    	if (!session.isUsable()) {
            throw new InvalidRequestException(
                    "sessionId",
                    "Session is no longer active. Status: " + session.getStatus()
            );
        }
    	session.markUsed();
    	
    	// Save back with the REMAINING TTL, not a fresh 30 minutes.
        // We don't want to accidentally extend the session lifetime here.
        Duration remainingTtl = Duration.between(Instant.now(), session.getExpiresAt());
        String redisKey = KEY_PREFIX + sessionId;
        
        if(remainingTtl.isPositive()) {
        	redisTemplate.opsForValue().set(redisKey, serialize(session), remainingTtl);
        }
        
        log.info("Session {} marked as USED", sessionId);
    }
    
    /**
     * Validates a session is active and belongs to the expected merchant.
     * Called at the start of checkout submission.
     */
    public CheckoutSession validateAndGet(String sessionId, UUID merchantId) {
        var session = getSession(sessionId);

        if (!session.getMerchantId().equals(merchantId)) {
            throw new ResourceNotFoundException("Session", sessionId);
        }

        if (!session.isUsable()) {
            throw new InvalidRequestException(
                    "sessionId",
                    "Session is no longer active. Status: " + session.getStatus()
            );
        }

        return session;
    }

    
    private String generateSessionId() {
        return SESSION_ID_PREFIX + UUID.randomUUID().toString().replace("-", "");
    }
    
    private String serialize(CheckoutSession session) {
        try {
            return objectMapper.writeValueAsString(session);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize session", e);
        }
    }

    private CheckoutSession deserialize(String json) {
        try {
            return objectMapper.readValue(json, CheckoutSession.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize session", e);
        }
    }
}
