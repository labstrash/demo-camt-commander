package com.example.commander.adapter.batch.trigger;

import com.example.commander.adapter.batch.config.BatchPipelineConfig;
import com.example.commander.domain.message.ReportType;
import com.example.commander.domain.report.ReportFrequency;
import com.example.commander.domain.report.ReportWindow;
import com.example.commander.domain.report.ReportingPeriodCalculator;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameter;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.launch.JobRestartException;
import org.springframework.stereotype.Component;

/**
 * Resolves a reporting window for {@code (reportType, frequency)} and launches {@link
 * BatchPipelineConfig}'s {@code reportPipelineJob} with it.
 *
 * <p>This is the stable seam {@code ReportSchedulingJob} calls into instead of resolving the
 * window and launching the job itself — Quartz is just another caller of this bean, not a
 * replacement for it.
 *
 * <p>A fresh {@code triggeredAt} parameter is added on the two "as of now"/reference-instant
 * launch paths, so a deliberate repeat trigger for the same {@code (reportType, frequency,
 * window)} — nothing currently does this, but the capability is kept available and covered by
 * tests — doesn't collide with an already-completed {@code JobInstance} of the same identifying
 * parameters. The Quartz-originated overload deliberately omits it: its {@code JobParameters}
 * are exactly {@code (reportType, frequency, windowStart, windowEnd)}, so Spring Batch's own
 * "refuse to relaunch an already-completed JobInstance" rule catches a genuine Quartz misfire
 * double-trigger for the same window before it ever reaches a second pipeline run — the
 * behavior {@code ReportSchedulingJob}'s own {@code JobInstanceAlreadyCompleteException}
 * handling exists to catch.
 */
@Component
public class ReportPipelineTrigger {

    private final JobOperator jobOperator;
    private final Job reportPipelineJob;
    private final ReportingPeriodCalculator periodCalculator;

    public ReportPipelineTrigger(
            JobOperator jobOperator, Job reportPipelineJob, ReportingPeriodCalculator periodCalculator) {
        this.jobOperator = jobOperator;
        this.reportPipelineJob = reportPipelineJob;
        this.periodCalculator = periodCalculator;
    }

    /**
     * Triggers a run for {@code reportType} at {@code frequency}, deriving the window as of
     * now.
     *
     * @param reportType the report type to run
     * @param frequency the report frequency to run
     * @return the resulting job execution
     */
    public JobExecution trigger(ReportType reportType, ReportFrequency frequency)
            throws JobExecutionAlreadyRunningException, JobRestartException, JobInstanceAlreadyCompleteException,
                    InvalidJobParametersException {
        return trigger(reportType, frequency, Instant.now());
    }

    /**
     * Triggers a run for {@code reportType} at {@code frequency}, deriving the window from
     * {@code referenceInstant} rather than "now" — for callers (tests, a manual re-run of a
     * past window) that need a specific reference point instead of the current instant.
     *
     * @param reportType the report type to run
     * @param frequency the report frequency to run
     * @param referenceInstant the instant to derive the reporting window from
     * @return the resulting job execution
     */
    public JobExecution trigger(ReportType reportType, ReportFrequency frequency, Instant referenceInstant)
            throws JobExecutionAlreadyRunningException, JobRestartException, JobInstanceAlreadyCompleteException,
                    InvalidJobParametersException {
        ReportWindow window = periodCalculator.calculateForReference(frequency, referenceInstant, reportType);
        return launch(reportType, frequency, window, true);
    }

    /**
     * Triggers a run for {@code reportType} at {@code frequency}, deriving the window directly
     * from a known scheduled fire time plus (for window-time frequencies) an explicit window
     * sequence and boundary list — the shape Quartz already has in hand from its own trigger
     * firing, rather than a bare reference instant it would otherwise have to re-derive
     * sequence/boundaries from (see {@link #trigger(ReportType, ReportFrequency, Instant)}).
     * This is the seam {@code ReportSchedulingJob} calls into — deliberately with deterministic
     * {@code JobParameters} (no {@code triggeredAt}), so Spring Batch's own duplicate-launch
     * guard can catch a Quartz misfire double-trigger for the same window. See the class
     * javadoc.
     *
     * @param reportType the report type to run
     * @param frequency the report frequency to run
     * @param fireTime the trigger's scheduled fire time
     * @param windowSequence the 0-based window-time slot index, or {@code null} for
     *     non-window-time frequencies
     * @param boundaries the configured window-time boundaries, or {@code null} for
     *     non-window-time frequencies
     * @return the resulting job execution
     */
    public JobExecution trigger(
            ReportType reportType,
            ReportFrequency frequency,
            Instant fireTime,
            Integer windowSequence,
            List<LocalTime> boundaries)
            throws JobExecutionAlreadyRunningException, JobRestartException, JobInstanceAlreadyCompleteException,
                    InvalidJobParametersException {
        ReportWindow window = periodCalculator.calculate(frequency, fireTime, windowSequence, boundaries);
        return launch(reportType, frequency, window, false);
    }

    private JobExecution launch(
            ReportType reportType, ReportFrequency frequency, ReportWindow window, boolean stampTriggeredAt)
            throws JobExecutionAlreadyRunningException, JobRestartException, JobInstanceAlreadyCompleteException,
                    InvalidJobParametersException {
        JobParametersBuilder builder = new JobParametersBuilder()
                .addString("reportType", reportType.name())
                .addString("reportFrequency", frequency.dbCode())
                .addJobParameter(new JobParameter<>("startDateTimeUtc", window.windowStartUtc(), Instant.class))
                .addJobParameter(new JobParameter<>("endDateTimeUtc", window.windowEndUtc(), Instant.class));
        if (stampTriggeredAt) {
            builder.addJobParameter(new JobParameter<>("triggeredAt", Instant.now(), Instant.class));
        }

        return jobOperator.start(reportPipelineJob, builder.toJobParameters());
    }
}
