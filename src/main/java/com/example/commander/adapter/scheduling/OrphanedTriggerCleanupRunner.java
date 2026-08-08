package com.example.commander.adapter.scheduling;

import com.example.commander.adapter.scheduling.ReportJobScheduleBuilder.ReportJobSchedule;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerKey;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Removes orphaned Quartz triggers that no longer exist in the current configuration.
 *
 * <p>Spring Boot's {@code SchedulerFactoryBean} adds and updates triggers, but never deletes
 * triggers that were registered previously but are no longer configured. This runner fills
 * that gap by comparing currently registered triggers against the expected set and removing
 * any orphaned triggers.
 *
 * <p>Safe for clustered environments: {@code unscheduleJob()} is idempotent, so concurrent
 * cleanup attempts from multiple nodes are harmless.
 *
 * <p><b>Blast radius is scoped by trigger group, not by class or package:</b> both {@code
 * currentKeys} and {@code expectedKeys} are drawn only from {@link
 * ReportJobScheduleBuilder#TRIGGER_GROUP} ({@code "camt-scheduling"}), so this runner can never
 * see or remove a trigger registered under any other group — e.g. {@code
 * DeadLetterRecoveryJob}/{@code AuditRetentionJob}'s triggers, which deliberately use a
 * different group ({@code QuartzSchedulerConfig}'s {@code "camt-mq-recovery"}) for exactly this
 * reason. Any future Quartz trigger — from any package — that reuses the {@code
 * "camt-scheduling"} group name would be treated as orphaned and deleted here, since {@code
 * expectedKeys} only ever reflects what {@link ReportJobScheduleBuilder} builds from {@code
 * commander.scheduling} config.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class OrphanedTriggerCleanupRunner implements ApplicationRunner {

    private final Scheduler scheduler;
    private final ReportJobSchedule expectedSchedule;

    @Override
    public void run(ApplicationArguments args) throws SchedulerException {
        Set<TriggerKey> expectedKeys = expectedTriggerKeys();
        Set<TriggerKey> currentKeys = currentlyRegisteredTriggerKeys();

        Set<TriggerKey> orphaned = new HashSet<>(currentKeys);
        orphaned.removeAll(expectedKeys);

        if (orphaned.isEmpty()) {
            log.info(
                    "Orphaned trigger cleanup: {} triggers registered, {} expected, none orphaned",
                    currentKeys.size(),
                    expectedKeys.size());
        } else {
            log.warn(
                    "Orphaned trigger cleanup: found {} orphaned trigger(s) no longer in configuration: {}",
                    orphaned.size(),
                    orphaned);
            for (TriggerKey key : orphaned) {
                removeOrphan(key);
            }
        }

        // spring.quartz.auto-startup=false means the scheduler is otherwise never started.
        // Starting it here, after cleanup, guarantees no trigger — orphaned or current —
        // can fire before this runner has finished reconciling scheduler state against config.
        log.info("Cleanup complete, starting scheduler");
        scheduler.start();
    }

    private void removeOrphan(TriggerKey key) {
        try {
            boolean removed = scheduler.unscheduleJob(key);
            if (removed) {
                log.warn("Removed orphaned trigger: {}", key);
            } else {
                log.info("Orphaned trigger {} was already removed (likely by another cluster node)", key);
            }
        } catch (SchedulerException e) {
            log.error("Failed to remove orphaned trigger: {}", key, e);
        }
    }

    private Set<TriggerKey> expectedTriggerKeys() {
        Set<TriggerKey> keys = new HashSet<>();
        for (Trigger trigger : expectedSchedule.triggers()) {
            keys.add(trigger.getKey());
        }
        return keys;
    }

    private Set<TriggerKey> currentlyRegisteredTriggerKeys() throws SchedulerException {
        return new HashSet<>(
                scheduler.getTriggerKeys(GroupMatcher.triggerGroupEquals(ReportJobScheduleBuilder.TRIGGER_GROUP)));
    }
}
