package prac.fin.payment.processor.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;

/**
 * Creates a single RazorpayClient instance shared across the application.
 *
 * WHY A SINGLE INSTANCE?
 *   RazorpayClient is thread-safe. Creating one per request would be
 *   wasteful. It initialises HTTP connection pools each time.
 *   A single Spring bean reuses connections across requests.
 *
 * The @EnableConfigurationProperties annotation tells Spring to
 * scan and bind RazorpayProperties. Without this, the properties
 * class wouldn't be registered as a bean even though it has
 * @ConfigurationProperties on it.
 */
@Configuration
@EnableConfigurationProperties(RazorpayProperties.class)
public class RazorpayConfig {

	@Bean
	public RazorpayClient razorpayClient(RazorpayProperties properties) throws RazorpayException {
		return new RazorpayClient(properties.getKeyId(), properties.getKeySecret());
	}
}
