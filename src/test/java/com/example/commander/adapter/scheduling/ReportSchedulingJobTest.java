package com.example.commander.adapter.scheduling;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.commander.adapter.batch.trigger.ReportPipelineTrigger;
import com.example.commander.domain.message.ReportType;
import com.example.commander.domain.report.ReportFrequency;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Trigger;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.dao.CannotAcquireLockException;

/**
 * Unit tests for {@link ReportSchedulingJob}'s orchestration logic: catch-ordering around
 * {@link JobInstanceAlreadyCompleteException}, the deadlock retry loop, and the {@code
 * BatchStatus} check on the returned {@link JobExecution} — window computation and the actual
 * pipeline launch are {@link ReportPipelineTrigger}'s concern, covered by {@code
 * ReportPipelineTriggerTest}, and mocked here.
 */
@ExtendWith(MockitoExtension.class)
class ReportSchedulingJobTest {

    @Mock
    private ReportPipelineTrigger reportPipelineTrigger;

    private ReportSchedulingJob job;

    @BeforeEach
    void setUp() {
        job = new ReportSchedulingJob(reportPipelineTrigger);
    }

    @Test
    void completesNormallyWhenPipelineJobCompletes() throws Exception {
        when(reportPipelineTrigger.trigger(
                        eq(ReportType.CAMT054C), eq(ReportFrequency.DAILY), any(), isNull(), isNull()))
                .thenReturn(completedExecution());

        job.execute(context("CAMT054C", "DAILY"));

        verify(reportPipelineTrigger, times(1))
                .trigger(eq(ReportType.CAMT054C), eq(ReportFrequency.DAILY), any(), isNull(), isNull());
    }

    @Test
    void duplicateFiringIsSwallowedNotThrown() throws Exception {
        when(reportPipelineTrigger.trigger(
                        eq(ReportType.CAMT054C), eq(ReportFrequency.DAILY), any(), isNull(), isNull()))
                .thenThrow(new JobInstanceAlreadyCompleteException("already complete"));

        job.execute(context("CAMT054C", "DAILY"));

        verify(reportPipelineTrigger, times(1))
                .trigger(eq(ReportType.CAMT054C), eq(ReportFrequency.DAILY), any(), isNull(), isNull());
    }

    @Test
    void nonCompletedStatusIsTreatedAsAFailure() throws Exception {
        when(reportPipelineTrigger.trigger(
                        eq(ReportType.CAMT054C), eq(ReportFrequency.DAILY), any(), isNull(), isNull()))
                .thenReturn(executionWithStatus(BatchStatus.FAILED));

        assertThatThrownBy(() -> job.execute(context("CAMT054C", "DAILY")))
                .isInstanceOf(JobExecutionException.class)
                .hasMessageContaining("CAMT054C")
                .hasCauseInstanceOf(IllegalStateException.class)
                .cause()
                .hasMessageContaining("FAILED");
    }

    @Test
    void transientFailureIsRetriedAndEventuallySucceeds() throws Exception {
        when(reportPipelineTrigger.trigger(
                        eq(ReportType.CAMT054C), eq(ReportFrequency.DAILY), any(), isNull(), isNull()))
                .thenThrow(new CannotAcquireLockException("deadlocked"))
                .thenReturn(completedExecution());

        job.execute(context("CAMT054C", "DAILY"));

        verify(reportPipelineTrigger, times(2))
                .trigger(eq(ReportType.CAMT054C), eq(ReportFrequency.DAILY), any(), isNull(), isNull());
    }

    @Test
    void transientFailureExhaustsRetriesAndFailsTheJob() throws Exception {
        when(reportPipelineTrigger.trigger(
                        eq(ReportType.CAMT054C), eq(ReportFrequency.DAILY), any(), isNull(), isNull()))
                .thenThrow(new CannotAcquireLockException("deadlocked"));

        assertThatThrownBy(() -> job.execute(context("CAMT054C", "DAILY")))
                .isInstanceOf(JobExecutionException.class)
                .hasCauseInstanceOf(CannotAcquireLockException.class);

        // MAX_LAUNCH_ATTEMPTS is 3 - not part of the public contract, but exhausting retries
        // rather than retrying forever (or just once) is the behavior worth pinning down.
        verify(reportPipelineTrigger, times(3))
                .trigger(eq(ReportType.CAMT054C), eq(ReportFrequency.DAILY), any(), isNull(), isNull());
    }

    @Test
    void missingReportTypeOrFrequencyFailsFastWithoutCallingTheTrigger() {
        JobExecutionContext context = context(null, "DAILY");

        assertThatThrownBy(() -> job.execute(context)).isInstanceOf(JobExecutionException.class);

        verifyNoInteractions(reportPipelineTrigger);
    }

    private static JobExecutionContext context(String reportType, String reportFrequency) {
        JobDataMap jobDataMap = new JobDataMap();
        if (reportType != null) {
            jobDataMap.put(ReportSchedulingJob.KEY_REPORT_TYPE, reportType);
        }
        if (reportFrequency != null) {
            jobDataMap.put(ReportSchedulingJob.KEY_REPORT_FREQUENCY, reportFrequency);
        }

        JobDetail jobDetail = mock(JobDetail.class);
        when(jobDetail.getJobDataMap()).thenReturn(jobDataMap);

        Trigger trigger = mock(Trigger.class);
        when(trigger.getJobDataMap()).thenReturn(new JobDataMap());

        JobExecutionContext context = mock(JobExecutionContext.class);
        when(context.getJobDetail()).thenReturn(jobDetail);
        when(context.getTrigger()).thenReturn(trigger);
        if (reportType != null && reportFrequency != null) {
            when(context.getScheduledFireTime()).thenReturn(Date.from(Instant.parse("2026-07-02T00:00:00Z")));
        }
        return context;
    }

    private static JobExecution completedExecution() {
        return executionWithStatus(BatchStatus.COMPLETED);
    }

    private static JobExecution executionWithStatus(BatchStatus status) {
        JobInstance jobInstance = new JobInstance(1L, "reportPipelineJob");
        JobExecution jobExecution = new JobExecution(1L, jobInstance, new JobParameters());
        jobExecution.setStatus(status);
        return jobExecution;
    }
}
