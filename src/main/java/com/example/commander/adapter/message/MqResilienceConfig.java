package com.example.commander.adapter.message;

import java.time.Clock;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

/**
 * Wires the framework pieces {@link ResilientMqSender} and {@link MqCircuitBreaker} build on.
 * Provides a {@link RetryTemplate} used programmatically (not {@code @Retryable}) and a
 * {@link Clock} bean for testability.
 */
@Configuration
public class MqResilienceConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public RetryTemplate mqRetryTemplate(MqResilienceProperties properties) {
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(
                properties.getRetryMaxAttempts(), Map.of(TransientMqFailureException.class, true));

        FixedBackOffPolicy backOffPolicy = new FixedBackOffPolicy();
        backOffPolicy.setBackOffPeriod(properties.getRetryBackoffMs());

        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setRetryPolicy(retryPolicy);
        retryTemplate.setBackOffPolicy(backOffPolicy);
        return retryTemplate;
    }
}
