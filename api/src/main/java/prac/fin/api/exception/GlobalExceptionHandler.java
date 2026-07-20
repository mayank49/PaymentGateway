package prac.fin.api.exception;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import lombok.extern.slf4j.Slf4j;
import prac.fin.payment.common.dto.ApiErrorResponse;
import prac.fin.payment.common.exception.IdempotencyConflictException;
import prac.fin.payment.common.exception.InvalidRequestException;
import prac.fin.payment.common.exception.InvalidStateTransitionException;
import prac.fin.payment.common.exception.PaymentDeclinedException;
import prac.fin.payment.common.exception.ProcessorException;

/**
 * Catches every exception thrown from a controller and returns
 * a consistent ApiErrorResponse JSON body.
 *
 * WHY THIS MATTERS:
 *   Without this, Spring would return its own default error format
 *   (with "timestamp", "path", "error" fields) which doesn't match
 *   our ApiErrorResponse schema and leaks implementation details.
 *
 * The rule: every exception has one place where it becomes an HTTP response.
 * Controllers never call response.setStatus() or catch exceptions themselves.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	/**
     * 400 
     * 
     * Bean Validation failures (@NotNull, @Min, @Pattern etc.)
     * Collects all field errors into the fieldErrors map.
     */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
		Map<String, String> fieldErrors =  ex.getBindingResult().getFieldErrors().stream().collect(
				Collectors.toMap(FieldError::getField, 
				fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value",
				(first, second) -> first));
		return ResponseEntity.badRequest().body(
				ApiErrorResponse.builder()
                .code("VALIDATION_ERROR")
                .message("Request validation failed")
                .fieldErrors(fieldErrors)
                .timestamp(Instant.now())
                .build());
	}
	
	/** 
	 * 400
	 *  
	 * Business-level invalid request (e.g. unsupported currency)  
	 */
    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidRequest(
            InvalidRequestException ex) {
 
        return ResponseEntity.badRequest().body(
                ApiErrorResponse.builder()
                        .code("INVALID_REQUEST")
                        .message(ex.getMessage())
                        .field(ex.getField())
                        .timestamp(Instant.now())
                        .build()
        );
    }
    
    /**
     * 409
     * 
     * Idempotency key already used.
     * Includes the existingIntentId so the merchant can fetch it directly.
     */
    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleIdempotencyConflict(
            IdempotencyConflictException ex) {
 
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                ApiErrorResponse.builder()
                        .code("IDEMPOTENCY_CONFLICT")
                        .message(ex.getMessage())
                        .intentId(ex.getExistingIntentId())
                        .timestamp(Instant.now())
                        .build()
        );
    }
	
    /** 
     * 409
     * 
     * Illegal state transition (e.g. canceling an already SUCCEEDED intent) 
     */
    @ExceptionHandler(InvalidStateTransitionException.class)
    public ResponseEntity<ApiErrorResponse> handleStateTransition(
            InvalidStateTransitionException ex) {
 
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                ApiErrorResponse.builder()
                        .code("INVALID_STATE_TRANSITION")
                        .message(ex.getMessage())
                        .timestamp(Instant.now())
                        .build()
        );
    }
    
    /**
     * 422
     * 
     * Payment declined by the bank.
     * Includes the processorCode so the merchant can show the right message.
     * e.g. "insufficient_funds" --> "Your card has insufficient funds."
     */
    @ExceptionHandler(PaymentDeclinedException.class)
    public ResponseEntity<ApiErrorResponse> handleDeclined(
            PaymentDeclinedException ex) {
 
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(
                ApiErrorResponse.builder()
                        .code("PAYMENT_DECLINED")
                        .message(ex.getMessage())
                        .timestamp(Instant.now())
                        .build()
        );
    }
    
    /**
     * 503
     * 
     * Processor (Razorpay/PayU) is unreachable or returned an unexpected error.
     * The intent status will be UNKNOWN and reconciliation will resolve it.
     * We log this at ERROR because it means money might be in an uncertain state.
     */
    @ExceptionHandler(ProcessorException.class)
    public ResponseEntity<ApiErrorResponse> handleProcessor(
            ProcessorException ex) {
 
        log.error("Processor error: {}", ex.getMessage(), ex);
 
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(
                ApiErrorResponse.builder()
                        .code("PROCESSOR_UNAVAILABLE")
                        .message("Payment processor is temporarily unavailable. " +
                                 "Your payment status will be updated shortly.")
                        .timestamp(Instant.now())
                        .build()
        );
    }
    
    /**
     * 500
     * 
     * Catch-all for anything we didn't anticipate.
     * Log the full stack trace but return a generic message to the client.
     * Never leak internal details (stack traces, SQL errors etc.).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.internalServerError().body(
                ApiErrorResponse.builder()
                        .code("INTERNAL_ERROR")
                        .message("An unexpected error occurred. Please try again.")
                        .timestamp(Instant.now())
                        .build()
        );
    }
	
}
