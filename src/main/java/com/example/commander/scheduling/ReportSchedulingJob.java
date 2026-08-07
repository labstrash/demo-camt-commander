package com.example.commander.scheduling;

import com.example.commander.adapter.batch.trigger.ReportPipelineTrigger;
import com.example.commander.domain.message.ReportType;
import com.example.commander.domain.report.ReportFrequency;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;

/**
 * Quartz job that executes report generation for a specific report type and frequency.
 *
 * <p>Reads {@code reportType}/{@code reportFrequency}/{@code boundaries} from the {@code
 * JobDetail}'s data map and {@code windowSequence} from the firing {@code Trigger}'s, then
 * delegates window computation and launch entirely to {@link ReportPipelineTrigger} — the
 * stable seam that class's own javadoc describes. This job owns only Quartz-specific execution
 * semantics: retrying a transient DB deadlock on job launch, checking the returned {@link
 * JobExecution}'s {@code BatchStatus} (a step failure doesn't throw out of {@code
 * JobOperator.start} — it comes back as a normally-returned, non-{@code COMPLETED} execution),
 * and treating {@link JobInstanceAlreadyCompleteException} as an expected, benign no-op rather
 * than a failure.
 *
 * <p>Synchronous (not async) so Quartz's own misfire/next-fire-time bookkeeping stays tied to
 * real completion, and so {@code @DisallowConcurrentExecution} keeps meaning what it says.
 *
 * <p>Uses {@code useProperties=true} in Quartz configuration, so all job data keys must be
 * strings.
 */
@Component
@DisallowConcurrentExecution
public class ReportSchedulingJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(ReportSchedulingJob.class);

    /** Job data key for the report type to generate. */
    public static final String KEY_REPORT_TYPE = "reportType";

    /** Job data key for the frequency label. */
    public static final String KEY_REPORT_FREQUENCY = "reportFrequency";

    /** Job data key for boundary times string (Pattern B). */
    public static final String KEY_BOUNDARIES = "boundaries";

    /** Job data key for window sequence number (0-based). */
    public static final String KEY_WINDOW_SEQUENCE = "windowSequence";

    // BATCH_JOB_INSTANCE is a shared table across every report type/frequency combination,
    // so concurrent firings (several triggers landing in the same second, possible in
    // production) can hit a genuine SQL Server deadlock on the INSERT. SQL Server's own
    // error text says it plainly: "chosen as the deadlock victim... Rerun the transaction."
    // This only covers deadlocks during JobInstance/JobExecution creation, before any step
    // runs - that's the sole point where a transient failure is thrown directly out of
    // start() rather than being absorbed into a normally-returned FAILED JobExecution (a
    // mid-step failure never reaches this catch; it comes back via getStatus() == FAILED,
    // handled by the status check below instead). Safe to retry from scratch: SQL Server
    // rolls back the deadlock victim's transaction, so nothing was committed yet.
    private static final int MAX_LAUNCH_ATTEMPTS = 3;
    private static final Duration LAUNCH_RETRY_BACKOFF = Duration.ofMillis(250);

    private final ReportPipelineTrigger reportPipelineTrigger;

    public ReportSchedulingJob(ReportPipelineTrigger reportPipelineTrigger) {
        this.reportPipelineTrigger = reportPipelineTrigger;
    }

    /**
     * Executes the report scheduling job.
     *
     * @param context Quartz job execution context containing job and trigger data
     * @throws JobExecutionException if required parameters are missing, the pipeline job fails
     *     to launch, or it completes with a non-{@code COMPLETED} status
     */
    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobDataMap jobData = context.getJobDetail().getJobDataMap();
        JobDataMap triggerData = context.getTrigger().getJobDataMap();

        String reportTypeStr = jobData.getString(KEY_REPORT_TYPE);
        String reportFrequencyStr = jobData.getString(KEY_REPORT_FREQUENCY);

        if (reportTypeStr == null || reportFrequencyStr == null) {
            throw new JobExecutionException("Missing required job parameters: reportType=" + reportTypeStr
                    + ", reportFrequency=" + reportFrequencyStr);
        }

        ReportType reportType = ReportType.valueOf(reportTypeStr);
        ReportFrequency frequency = ReportFrequency.fromConfig(reportFrequencyStr);
        Instant fireTime = context.getScheduledFireTime().toInstant();

        String sequenceStr = triggerData.getString(KEY_WINDOW_SEQUENCE);
        Integer windowSequence = sequenceStr != null ? Integer.valueOf(sequenceStr) : null;

        String boundariesCsv = jobData.getString(KEY_BOUNDARIES);
        List<LocalTime> boundaries =
                boundariesCsv != null ? ReportJobScheduleBuilder.parseBoundaries(boundariesCsv) : null;

        try {
            JobExecution jobExecution =
                    startWithDeadlockRetry(reportType, frequency, fireTime, windowSequence, boundaries);

            // JobOperator.start() is synchronous but only throws for launch-time problems
            // (already-running, already-complete, invalid params). A genuine step failure
            // doesn't propagate out of start() at all - it comes back as a normally-returned
            // JobExecution with BatchStatus.FAILED, so that has to be checked explicitly or
            // Quartz sees no exception and treats a failed pipeline run as a successful firing.
            if (jobExecution.getStatus() != BatchStatus.COMPLETED) {
                throw new IllegalStateException("reportPipelineJob did not complete successfully for " + reportType
                        + "/" + reportFrequencyStr + ": status=" + jobExecution.getStatus());
            }

            log.info(
                    "Job completed successfully: reportType={}, frequency={}, status={}",
                    reportType,
                    frequency,
                    jobExecution.getStatus());

        } catch (JobInstanceAlreadyCompleteException e) {
            // Expected/benign: the same (reportType, reportFrequency, window) already ran to
            // completion - most likely a Quartz misfire double-trigger. Must be caught before
            // the general Exception catch below, or a correctly-skipped duplicate firing would
            // be logged/alerted on as a real job failure.
            log.info(
                    "Skipping duplicate firing: reportType={}, frequency={}, fireTime={} already completed",
                    reportType,
                    frequency,
                    fireTime);
        } catch (Exception e) {
            log.error("Job failed: reportType={}, frequency={}, fireTime={}", reportType, frequency, fireTime, e);
            throw new JobExecutionException(
                    "Report scheduling job failed for " + reportType + "/" + frequency, e, false);
        }
    }

    /**
     * Launches via {@link ReportPipelineTrigger}, retrying a bounded number of times on a
     * transient data-access failure (e.g. a SQL Server deadlock on {@code
     * BATCH_JOB_INSTANCE}). Does not retry {@link JobInstanceAlreadyCompleteException} or any
     * other checked exception from the trigger call — those propagate immediately, unchanged.
     */
    private JobExecution startWithDeadlockRetry(
            ReportType reportType,
            ReportFrequency frequency,
            Instant fireTime,
            Integer windowSequence,
            List<LocalTime> boundaries)
            throws Exception {
        TransientDataAccessException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_LAUNCH_ATTEMPTS; attempt++) {
            try {
                return reportPipelineTrigger.trigger(reportType, frequency, fireTime, windowSequence, boundaries);
            } catch (TransientDataAccessException e) {
                lastFailure = e;
                log.warn(
                        "Transient failure launching reportPipelineJob (attempt {}/{}): {}",
                        attempt,
                        MAX_LAUNCH_ATTEMPTS,
                        e.getMessage());
                if (attempt < MAX_LAUNCH_ATTEMPTS) {
                    sleepBeforeRetry();
                }
            }
        }
        throw lastFailure;
    }

    private static void sleepBeforeRetry() {
        try {
            Thread.sleep(LAUNCH_RETRY_BACKOFF.toMillis());
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }
}
