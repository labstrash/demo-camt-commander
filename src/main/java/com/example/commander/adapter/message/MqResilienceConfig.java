package com.example.commander.adapter.message;

import java.time.Clock;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

/** Provides {@link Clock} and {@link RetryTemplate} for MQ resilience. */
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
