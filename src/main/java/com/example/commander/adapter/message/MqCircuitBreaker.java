package com.example.commander.adapter.message;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/**
 * Small hand-rolled circuit breaker gating {@link ResilientMqSender}'s calls to MQ, so a
 * sustained outage doesn't make every message in a chunk pay a fresh connection-timeout cost
 * before dead-lettering.
 *
 * <p>No half-open state machine beyond "has the cool-down window passed": once open, a call
 * after {@code openUntilTime} elapses is let through as a probe — success closes the breaker and
 * resets the failure count, failure re-opens it (pushing {@code openUntilTime} out again from
 * now, which is also how a repeatedly-failing probe keeps resetting the cool-down).
 *
 * <p>Threshold and cool-down duration are configured via {@link MqResilienceProperties} and
 * are placeholder defaults — sizing them needs real outage/recovery data, not available at
 * implementation time.
 */
@Component
public class MqCircuitBreaker {

    private static final Instant NEVER_OPEN = Instant.EPOCH;

    private final int failureThreshold;
    private final Duration coolDown;
    private final Clock clock;

    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicReference<Instant> openUntilTime = new AtomicReference<>(NEVER_OPEN);

    public MqCircuitBreaker(MqResilienceProperties properties, Clock clock) {
        this.failureThreshold = properties.getBreakerFailureThreshold();
        this.coolDown = Duration.ofSeconds(properties.getBreakerCoolDownSeconds());
        this.clock = clock;
    }

    /**
     * Returns whether a send attempt should be allowed through right now.
     *
     * @return {@code true} if the breaker is closed, or open but the cool-down window has
     *     elapsed (letting a probe call through); {@code false} if genuinely open
     */
    public boolean isRequestAllowed() {
        return !clock.instant().isBefore(openUntilTime.get());
    }

    /** Records a successful send — closes the breaker and resets the failure count. */
    public void recordSuccess() {
        consecutiveFailures.set(0);
        openUntilTime.set(NEVER_OPEN);
    }

    /**
     * Records a send that exhausted every retry attempt and still failed. Opens the breaker
     * (or, if already at/above threshold, pushes {@code openUntilTime} out again) once
     * consecutive failures reach the configured threshold.
     */
    public void recordFailure() {
        if (consecutiveFailures.incrementAndGet() >= failureThreshold) {
            openUntilTime.set(clock.instant().plus(coolDown));
        }
    }
}
