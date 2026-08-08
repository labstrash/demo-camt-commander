package com.example.commander.adapter.observability;

import com.example.commander.adapter.message.MqCircuitBreaker;
import com.example.commander.port.DeadLetterMessageRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.RequiredArgsConstructor;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.springframework.stereotype.Component;

/**
 * Registers this application's custom Grafana/Mimir-facing gauges, polled at scrape time
 * (no periodic background job needed — each {@link Gauge} calls straight through to its
 * source on every collection, same as the {@link DeadLetterMessageRepository} count queries
 * below would be if called directly).
 *
 * <ul>
 *   <li>{@code commander.deadletter.backlog{status=PENDING_RETRY|FAILED}} — current row counts,
 *       the primary "is the resilience tier keeping up" signal.
 *   <li>{@code commander.mq.circuit_breaker.up} — 1 while {@link MqCircuitBreaker} is allowing
 *       sends, 0 while genuinely open. Same {@code up}-means-healthy polarity as Prometheus's
 *       own built-in {@code up} metric.
 *   <li>{@code commander.quartz.scheduler.up} — 1 once the scheduler has actually been started
 *       (see {@code OrphanedTriggerCleanupRunner} — {@code spring.quartz.auto-startup=false}
 *       means this isn't automatic), 0 otherwise.
 * </ul>
 */
@RequiredArgsConstructor
@Component
public class CommanderMetricsBinder implements MeterBinder {

    private final DeadLetterMessageRepository deadLetterMessageRepository;
    private final MqCircuitBreaker circuitBreaker;
    private final Scheduler scheduler;

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder(
                        "commander.deadletter.backlog",
                        deadLetterMessageRepository,
                        repo -> repo.countByStatus("PENDING_RETRY"))
                .tag("status", "PENDING_RETRY")
                .description("Current CAMT.DeadLetterMessage row count by status")
                .register(registry);

        Gauge.builder("commander.deadletter.backlog", deadLetterMessageRepository, repo -> repo.countByStatus("FAILED"))
                .tag("status", "FAILED")
                .description("Current CAMT.DeadLetterMessage row count by status")
                .register(registry);

        Gauge.builder("commander.mq.circuit_breaker.up", circuitBreaker, cb -> cb.isRequestAllowed() ? 1 : 0)
                .description("1 while the MQ circuit breaker is allowing sends, 0 while open")
                .register(registry);

        Gauge.builder("commander.quartz.scheduler.up", scheduler, sch -> isStarted(sch) ? 1 : 0)
                .description("1 once the Quartz scheduler has been started, 0 otherwise")
                .register(registry);
    }

    private static boolean isStarted(Scheduler scheduler) {
        try {
            return scheduler.isStarted() && !scheduler.isShutdown();
        } catch (SchedulerException e) {
            return false;
        }
    }
}
