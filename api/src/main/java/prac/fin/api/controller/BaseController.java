package prac.fin.api.controller;

import jakarta.servlet.http.HttpServletRequest;
import prac.fin.api.filter.ApiKeyAuthFilter;
import prac.fin.payment.domain.entity.Merchant;

public class BaseController {

	protected Merchant getMerchant(HttpServletRequest request) {
		Object merchant = request.getAttribute(ApiKeyAuthFilter.MERCHANT_ATTRIBUTE);
		if(merchant == null) {
			throw new IllegalStateException("No authenticated merchant on request");
		}
		return (Merchant) merchant;
	}
}
