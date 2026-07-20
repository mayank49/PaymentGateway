package prac.fin.payment.common.dto;

import java.time.Instant;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Standard error response returned for every failed API call.
 *
 * Consistent structure means the merchant's code can always parse errors
 * the same way, regardless of what went wrong.
 *
 * Modelled after Stripe's error format:
 * {
 *   "error": {
 *     "code": "insufficient_funds",
 *     "message": "Your card has insufficient funds",
 *     "field": null,
 *     "intentId": "pi_abc123",
 *     "timestamp": "2024-01-01T10:00:00"
 *   }
 * }
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiErrorResponse {

	private String code;
	private String message;
	private String field;
	private Map<String, String> fieldErrors;
	private String intentId;
	private Instant timestamp;
}
