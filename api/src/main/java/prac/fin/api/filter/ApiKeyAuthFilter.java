package prac.fin.api.filter;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import prac.fin.payment.common.dto.ApiErrorResponse;
import prac.fin.payment.domain.entity.Merchant;
import prac.fin.payment.domain.repository.MerchantRepository;

@Slf4j
@RequiredArgsConstructor
public class ApiKeyAuthFilter extends OncePerRequestFilter {
	
	public static final String MERCHANT_ATTRIBUTE = "authenticated_merchant";
	 
    private static final String AUTH_HEADER = "Authorization";
	private static final String BEARER_PREFIX = "Bearer ";
    
    private final MerchantRepository merchantRepository;
    private final ObjectMapper objectMapper;
    private final List<String> publicPaths;

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		
		String authHeader = request.getHeader(AUTH_HEADER);
		if(authHeader == null || authHeader.isEmpty()) {
			writeUnauthorized(response, "Missing or invalid Authorization header");
			return;
		}
		
		String rawApiKey = authHeader.substring(BEARER_PREFIX.length(), authHeader.length());
		if(rawApiKey.isBlank()) {
			writeUnauthorized(response, "API key is empty");
			return;
		}
		
		String apiKeyHash = sha256(rawApiKey);
		Optional<Merchant> merchant = merchantRepository.findByApiKeyHashAndActiveTrue(apiKeyHash);
		if(merchant.isEmpty()) {
			writeUnauthorized(response, "Invalid API key");
			return;
		}
		
		request.setAttribute(MERCHANT_ATTRIBUTE, merchant.get());
		filterChain.doFilter(request, response);
	}
	
	/**
     * Skip authentication for public endpoints that don't need a merchant context.
     * Actuator health checks, webhook callbacks from processors etc.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
    	String requestPath = request.getRequestURI();
    	 
        return publicPaths.stream().anyMatch(configuredUrl -> {
            try {
                // Extract just the path from the full URL in config
                String configuredPath = URI.create(configuredUrl).getPath();
                return requestPath.startsWith(configuredPath);
            } catch (IllegalArgumentException e) {
                // If it's already just a path (e.g. "/actuator"), compare directly
                return requestPath.startsWith(configuredUrl);
            }
        });
    }
	
	private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
		response.setStatus(HttpStatus.UNAUTHORIZED.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		ApiErrorResponse error = ApiErrorResponse.builder()
				.code("UNAUTHORIZED")
				.message(message)
				.timestamp(Instant.now())
				.build();
		response.getWriter().write(objectMapper.writeValueAsString(error));
	}
	
	private String sha256(String input) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

}
