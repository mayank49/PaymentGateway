package prac.fin.payment.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSessionRequest {
	
	private String intentId;
	
	private String successUrl;
	
	private String cancelUrl;
}
