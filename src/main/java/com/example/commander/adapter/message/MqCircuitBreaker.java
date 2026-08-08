package com.example.commander.adapter.message;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/**
 * Circuit breaker gating {@link ResilientMqSender}'s MQ calls, so a sustained outage doesn't
 * make every message pay a fresh connection-timeout before dead-lettering.
 *
 * <p>Hand-rolled rather than resilience4j (no dependency pulled in for it) — the state machine
 * this class needs is small enough that reimplementing it is cheaper than adopting and
 * configuring a general-purpose library for it.
 *
 * <p><b>States:</b>
 * <ul>
 *   <li><b>CLOSED</b> — normal operation. Calls are allowed; failures are counted.</li>
 *   <li><b>OPEN</b> — calls are rejected immediately, without attempting the operation, until
 *       the cool-down elapses.</li>
 *   <li><b>HALF-OPEN</b> — after the cool-down, the next call is let through as a probe.
 *       Success closes the breaker; failure re-opens it and restarts the cool-down.</li>
 * </ul>
 *
 * <p><b>How the states map onto the fields:</b> there's no explicit state enum — the state is
 * derived entirely from {@code openUntilTime} vs. the current time:
 * <ul>
 *   <li>CLOSED — {@code openUntilTime == NEVER_OPEN} (a sentinel permanently in the past).</li>
 *   <li>OPEN — {@code openUntilTime} is set to {@code now + coolDown} at the moment the
 *       breaker trips, so every call up to that instant is rejected.</li>
 *   <li>HALF-OPEN — implicit, not stored anywhere: once {@code now >= openUntilTime}, {@link
 *       #isRequestAllowed()} starts returning {@code true} again, and the very next caller's
 *       {@link #recordSuccess()}/{@link #recordFailure()} decides whether that was the probe
 *       that re-closes the breaker or the failure that re-opens it. Nothing distinguishes "the
 *       probe call" from an ordinary call — the first request after the cool-down simply plays
 *       that role.</li>
 * </ul>
 *
 * <p><b>Example</b> (threshold=5, cool-down=30s): the 5th consecutive failure sets {@code
 * openUntilTime = now + 30s}; every call in that window short-circuits via {@code
 * isRequestAllowed() == false}, without {@link ResilientMqSender} attempting a connection at
 * all. Once 30s pass, the next call is let through; if it succeeds, {@link #recordSuccess()}
 * resets {@code consecutiveFailures} to 0 and {@code openUntilTime} back to {@code NEVER_OPEN}
 * (CLOSED); if it fails, {@link #recordFailure()} pushes {@code openUntilTime} out another 30s
 * from now (still OPEN, cool-down restarted).
 *
 * <p><b>Thread-safety:</b> {@link ResilientMqSender} can be called concurrently (e.g. by
 * multiple batch-chunk threads), so both fields are lock-free atomics rather than
 * {@code synchronized} state — {@code isRequestAllowed()} never blocks a caller.
 *
 * <p>Threshold and cool-down are placeholder defaults — size them from real outage data.
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
     * @return {@code true} if CLOSED, or OPEN with the cool-down elapsed (probe allowed);
     *     {@code false} if genuinely OPEN
     */
    public boolean isRequestAllowed() {
        return !clock.instant().isBefore(openUntilTime.get());
    }

    /** Records a successful send — closes the breaker and resets the failure count. */
    public void recordSuccess() {
        consecutiveFailures.set(0);
        openUntilTime.set(NEVER_OPEN);
    }

    /** Records a failure; opens the breaker (or extends the cool-down) once at threshold. */
    public void recordFailure() {
        if (consecutiveFailures.incrementAndGet() >= failureThreshold) {
            openUntilTime.set(clock.instant().plus(coolDown));
        }
    }
}
