package prac.fin.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Application entry point.
 *
 * @SpringBootApplication scans prac.fin.api and all sub-packages.
 * But our beans live in prac.fin.* So we explicitly tell Spring 
 * to also scan those packages.
 *
 * @EnableJpaAuditing activates @CreatedDate and @LastModifiedDate
 * on BaseEntity. Without this annotation, those fields are never populated.
 */
@SpringBootApplication(scanBasePackages = "prac.fin")
@EnableJpaAuditing
public class PaymentGatewayApplication {
	
	public static void main(String[] args) {
		SpringApplication.run(PaymentGatewayApplication.class, args);
	}
}
