package prac.fin.api.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CheckoutResponse {
    private final String  status;        // "success", "requires_action", "pending"
    private final String  redirectUrl;   // for requires_action
    private final String  successUrl;    // for success
    private final String  processorPaymentId;
    private final String  message;

    public static CheckoutResponse success(String successUrl, String processorPaymentId) {
        return CheckoutResponse.builder()
                .status("success")
                .successUrl(successUrl)
                .processorPaymentId(processorPaymentId)
                .build();
    }

    public static CheckoutResponse requiresAction(String redirectUrl) {
        return CheckoutResponse.builder()
                .status("requires_action")
                .redirectUrl(redirectUrl)
                .build();
    }

    public static CheckoutResponse pending(String message) {
        return CheckoutResponse.builder()
                .status("pending")
                .message(message)
                .build();
    }
}
