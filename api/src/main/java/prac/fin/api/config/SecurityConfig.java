package prac.fin.api.config;

import java.util.List;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import prac.fin.api.filter.ApiKeyAuthFilter;
import prac.fin.payment.domain.repository.MerchantRepository;
import prac.fin.payment.processor.config.PayUProperties;
import prac.fin.payment.processor.config.RazorpayProperties;
import prac.fin.payment.processor.config.StripeProperties;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

	private final MerchantRepository merchantRepository;
	private final ObjectMapper objectMapper;
	private final RazorpayProperties razorpayProperties;
	private final PayUProperties payUProperties;
	private final StripeProperties stripeProperties;
	
	@Bean
    public ApiKeyAuthFilter apiKeyAuthFilter() {
        return new ApiKeyAuthFilter(merchantRepository, objectMapper, publicPaths());
    }
	
	@Bean
	private SecurityFilterChain securityFilterChain(HttpSecurity http, ApiKeyAuthFilter apiKeyAuthFilter) throws Exception {
		return http
				.csrf(AbstractHttpConfigurer:: disable) // use this or http.csrf(csrf -> csrf.disable())
				.sessionManagement(session ->
						session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/actuator/**").permitAll()
						.requestMatchers("/webhooks/**").permitAll()
						.anyRequest().authenticated())
				.addFilterBefore(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class).build();
	}
	
	/**
     * Prevents Spring Boot from auto-registering ApiKeyAuthFilter in the
     * servlet container filter chain. Without this it would run twice.
     * Once via Spring Security and once directly via the servlet container.
     * Setting enabled=false means only Spring Security's chain runs it.
     */
	@Bean
    public FilterRegistrationBean<ApiKeyAuthFilter> apiKeyFilterRegistration(
            ApiKeyAuthFilter filter) {
        var registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
	
	private List<String> publicPaths() {
        return List.of(
                "/actuator",
                razorpayProperties.getCallbackUrl(),
                payUProperties.getSuccessUrl(),
                payUProperties.getFailureUrl()
        );
    }
}
