package com.example.commander.scheduling;

import com.example.commander.adapter.batch.reader.ReportPipelineItemReader;
import com.example.commander.adapter.batch.trigger.ReportPipelineTrigger;
import com.example.commander.config.SchedulingProperties;
import com.example.commander.domain.message.ReportType;
import com.example.commander.domain.report.ReportFrequency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Temporary stand-in for Quartz (deferred to the rewrite's last phase): every 15 minutes,
 * re-triggers {@link ReportPipelineTrigger} for every report type in every configured {@code
 * commander.scheduling} schedule, so the reader/processor/writer pipeline runs and logs real
 * messages on a visible cadence without Quartz wired up yet.
 *
 * <p>The 15-minute poll interval is independent of each schedule's own configured frequency —
 * {@link com.example.commander.domain.report.ReportingPeriodCalculator} still derives the
 * correct reporting window from each report type's actual {@link ReportFrequency} regardless
 * of how often this runs; this only controls how often that derivation happens, not what
 * window it produces.
 *
 * <p>One report type's failure doesn't stop the sweep — logged and skipped, so a single bad
 * config can't block every other report type from running this firing.
 */
@Component
public class PeriodicReportPipelineRunner {

    private static final Logger log = LoggerFactory.getLogger(PeriodicReportPipelineRunner.class);

    private final ReportPipelineTrigger reportPipelineTrigger;
    private final SchedulingProperties schedulingProperties;

    public PeriodicReportPipelineRunner(
            ReportPipelineTrigger reportPipelineTrigger, SchedulingProperties schedulingProperties) {
        this.reportPipelineTrigger = reportPipelineTrigger;
        this.schedulingProperties = schedulingProperties;
    }

    @Scheduled(cron = "0 0/15 * * * *")
    public void triggerAllConfiguredReportTypes() {
        for (SchedulingProperties.Schedule schedule : schedulingProperties.getSchedules()) {
            ReportFrequency frequency = ReportFrequency.fromConfig(schedule.getFrequency());
            for (ReportType reportType : schedule.getReportTypes()) {
                triggerOne(reportType, frequency);
            }
        }
    }

    private void triggerOne(ReportType reportType, ReportFrequency frequency) {
        try {
            JobExecution execution = reportPipelineTrigger.trigger(reportType, frequency);
            logSummary(reportType, frequency, execution);
        } catch (Exception ex) {
            log.error("Periodic pipeline trigger failed for reportType={}, frequency={}", reportType, frequency, ex);
        }
    }

    private void logSummary(ReportType reportType, ReportFrequency frequency, JobExecution execution) {
        // Deliberately NOT StepExecution.getReadCount(): that counts read() calls, i.e.
        // messages (ReportPipelineItemReader.read() returns one fanned-out message per call),
        // which is the same number as messagesWritten below and not what "configsRead" means.
        // ReportPipelineItemReader tracks the actual per-page ReportConfig count itself and
        // persists it into the step's execution context under CONFIGS_READ_COUNT_KEY.
        long configsRead = 0;
        long writeCount = 0;
        for (StepExecution step : execution.getStepExecutions()) {
            configsRead += step.getExecutionContext().getLong(ReportPipelineItemReader.CONFIGS_READ_COUNT_KEY, 0L);
            writeCount += step.getWriteCount();
        }
        log.info(
                "Triggered reportType={}, frequency={}: status={}, configsRead={}, messagesWritten={}",
                reportType,
                frequency,
                execution.getStatus(),
                configsRead,
                writeCount);
    }
}
