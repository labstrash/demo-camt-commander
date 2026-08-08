package com.example.commander.adapter.observability;

import lombok.RequiredArgsConstructor;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Surfaces whether the Quartz scheduler has actually been started. {@code
 * spring.quartz.auto-startup=false} means this isn't automatic — {@code
 * OrphanedTriggerCleanupRunner} starts it explicitly, after reconciling orphaned triggers — so
 * this indicator catches the case where startup got stuck before that point ever ran.
 */
@RequiredArgsConstructor
@Component
public class QuartzSchedulerHealthIndicator implements HealthIndicator {

    private final Scheduler scheduler;

    @Override
    public Health health() {
        try {
            if (scheduler.isShutdown()) {
                return Health.down().withDetail("reason", "scheduler shut down").build();
            }
            if (!scheduler.isStarted()) {
                return Health.down()
                        .withDetail("reason", "scheduler not yet started")
                        .build();
            }
            return Health.up().build();
        } catch (SchedulerException e) {
            return Health.down(e).build();
        }
    }
}
