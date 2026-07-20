package prac.fin.webhook.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables @Scheduled on WebhookScheduler.
 * Without @EnableScheduling, @Scheduled methods are silently ignored.
 */
@Configuration
@EnableScheduling
public class WebhookConfig {

}
