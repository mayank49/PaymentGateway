package prac.fin.api.config;

import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import prac.fin.payment.processor.config.PayUProperties;
import prac.fin.payment.processor.config.RazorpayProperties;
import prac.fin.payment.processor.config.StripeProperties;

/**
 * Misc application beans.
 *
 * @EnableConfigurationProperties registers PayU and Stripe properties here.
 * Razorpay is registered in its own RazorpayConfig inside processor-service.
 */
@Configuration
@EnableConfigurationProperties({PayUProperties.class, StripeProperties.class, RazorpayProperties.class})
public class AppConfig {
	
	@Bean
	public RestTemplate restTemplate(RestTemplateBuilder builder) {
		return builder.setConnectTimeout(Duration.ofSeconds(3))
				.setReadTimeout(Duration.ofSeconds(30))
				.build();
	}
	
	@Bean
    public ObjectMapper objectMapper() {
        var mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }
}
