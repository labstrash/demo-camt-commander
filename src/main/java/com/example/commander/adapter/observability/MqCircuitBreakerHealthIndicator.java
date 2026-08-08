package com.example.commander.adapter.observability;

import com.example.commander.adapter.message.MqCircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Surfaces {@link MqCircuitBreaker#isRequestAllowed()} on {@code /actuator/health} — {@code
 * UP} while the breaker is closed (or its cool-down has elapsed), {@code DOWN} while genuinely
 * open, so "MQ delivery is currently degraded" is visible without digging through logs.
 */
@RequiredArgsConstructor
@Component
public class MqCircuitBreakerHealthIndicator implements HealthIndicator {

    private final MqCircuitBreaker circuitBreaker;

    @Override
    public Health health() {
        if (circuitBreaker.isRequestAllowed()) {
            return Health.up().build();
        }
        return Health.down().withDetail("reason", "circuit breaker open").build();
    }
}
