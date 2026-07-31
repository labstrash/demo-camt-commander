package com.example.commander.adapter.batch.trigger;

import com.example.commander.adapter.batch.config.BatchPipelineConfig;
import com.example.commander.domain.report.ReportFrequency;
import com.example.commander.domain.report.ReportWindow;
import com.example.commander.domain.report.ReportingPeriodCalculator;
import java.time.Instant;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameter;
import org.springframework.batch.core.job.parameters.JobParameters;
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
 * <p>This is the stable seam a future Quartz job is meant to call into instead of resolving
 * the window and launching the job itself — Quartz becomes just another caller of this
 * unchanged bean, not a replacement for it. Today, with no scheduler wired up yet, it's called
 * by tests and (optionally) a manual trigger endpoint.
 *
 * <p>A fresh {@code triggeredAt} parameter is added to every launch so repeated triggers for
 * the same {@code (reportType, frequency, window)} don't collide with an already-completed
 * {@code JobInstance} of the same identifying parameters.
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
    public JobExecution trigger(String reportType, ReportFrequency frequency)
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
    public JobExecution trigger(String reportType, ReportFrequency frequency, Instant referenceInstant)
            throws JobExecutionAlreadyRunningException, JobRestartException, JobInstanceAlreadyCompleteException,
                    InvalidJobParametersException {
        ReportWindow window = periodCalculator.calculateForReference(frequency, referenceInstant, reportType);

        JobParameters jobParameters = new JobParametersBuilder()
                .addString("reportType", reportType)
                .addString("reportFrequency", frequency.dbCode())
                .addJobParameter(new JobParameter<>("startDateTimeUtc", window.windowStartUtc(), Instant.class))
                .addJobParameter(new JobParameter<>("endDateTimeUtc", window.windowEndUtc(), Instant.class))
                .addJobParameter(new JobParameter<>("triggeredAt", Instant.now(), Instant.class))
                .toJobParameters();

        return jobOperator.start(reportPipelineJob, jobParameters);
    }
}
