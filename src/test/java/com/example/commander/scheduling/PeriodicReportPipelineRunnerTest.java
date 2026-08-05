package com.example.commander.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.commander.adapter.batch.reader.ReportPipelineItemReader;
import com.example.commander.adapter.batch.trigger.ReportPipelineTrigger;
import com.example.commander.config.SchedulingProperties;
import com.example.commander.domain.message.ReportType;
import com.example.commander.domain.report.ReportFrequency;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.ExecutionContext;

/**
 * Covers the two behaviors {@link PeriodicReportPipelineRunner}'s javadoc promises but nothing
 * previously verified: a failing report type doesn't stop the rest of the sweep, and the
 * per-firing summary log aggregates {@code configsRead}/{@code messagesWritten} across every
 * {@link StepExecution} of a triggered job — not just the first.
 */
@ExtendWith(MockitoExtension.class)
class PeriodicReportPipelineRunnerTest {

    @Mock
    private ReportPipelineTrigger reportPipelineTrigger;

    @Mock
    private SchedulingProperties schedulingProperties;

    private PeriodicReportPipelineRunner runner;
    private Logger runnerLogger;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        runner = new PeriodicReportPipelineRunner(reportPipelineTrigger, schedulingProperties);

        runnerLogger = (Logger) LoggerFactory.getLogger(PeriodicReportPipelineRunner.class);
        runnerLogger.setLevel(Level.DEBUG);
        logAppender = new ListAppender<>();
        logAppender.start();
        runnerLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        runnerLogger.detachAppender(logAppender);
        runnerLogger.setLevel(null);
    }

    @Test
    void triggersEveryReportTypeInEveryConfiguredSchedule() throws Exception {
        when(schedulingProperties.getSchedules())
                .thenReturn(List.of(
                        schedule("DAILY", ReportType.CAMT054C, ReportType.CAMT054D),
                        schedule("EVERY_30_MIN", ReportType.CAMT052B)));
        when(reportPipelineTrigger.trigger(any(), any())).thenReturn(completedExecution());

        runner.triggerAllConfiguredReportTypes();

        verify(reportPipelineTrigger).trigger(ReportType.CAMT054C, ReportFrequency.DAILY);
        verify(reportPipelineTrigger).trigger(ReportType.CAMT054D, ReportFrequency.DAILY);
        verify(reportPipelineTrigger).trigger(ReportType.CAMT052B, ReportFrequency.EVERY_30_MIN);
    }

    @Test
    void oneReportTypesFailureDoesNotStopTheOthersInTheSameSchedule() throws Exception {
        when(schedulingProperties.getSchedules())
                .thenReturn(List.of(schedule("DAILY", ReportType.CAMT054C, ReportType.CAMT054D)));
        when(reportPipelineTrigger.trigger(eq(ReportType.CAMT054C), eq(ReportFrequency.DAILY)))
                .thenThrow(new RuntimeException("boom"));
        when(reportPipelineTrigger.trigger(eq(ReportType.CAMT054D), eq(ReportFrequency.DAILY)))
                .thenReturn(completedExecution());

        runner.triggerAllConfiguredReportTypes();

        verify(reportPipelineTrigger).trigger(ReportType.CAMT054C, ReportFrequency.DAILY);
        verify(reportPipelineTrigger).trigger(ReportType.CAMT054D, ReportFrequency.DAILY);
        assertThat(logAppender.list).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);
            assertThat(event.getFormattedMessage()).contains("CAMT054C").contains("DAILY");
        });
    }

    @Test
    void oneSchedulesFailureDoesNotStopTheNextScheduleFromRunning() throws Exception {
        when(schedulingProperties.getSchedules())
                .thenReturn(
                        List.of(schedule("DAILY", ReportType.CAMT054C), schedule("EVERY_30_MIN", ReportType.CAMT052B)));
        when(reportPipelineTrigger.trigger(eq(ReportType.CAMT054C), eq(ReportFrequency.DAILY)))
                .thenThrow(new RuntimeException("boom"));
        when(reportPipelineTrigger.trigger(eq(ReportType.CAMT052B), eq(ReportFrequency.EVERY_30_MIN)))
                .thenReturn(completedExecution());

        runner.triggerAllConfiguredReportTypes();

        verify(reportPipelineTrigger).trigger(ReportType.CAMT052B, ReportFrequency.EVERY_30_MIN);
    }

    @Test
    void logsTheConfigsReadAndMessagesWrittenSummedAcrossEveryStepExecution() throws Exception {
        when(schedulingProperties.getSchedules()).thenReturn(List.of(schedule("DAILY", ReportType.CAMT054C)));
        JobExecution execution = completedExecution();
        execution.addStepExecutions(
                List.of(stepExecution(execution, 1L, 3L, 5L), stepExecution(execution, 2L, 2L, 4L)));
        when(reportPipelineTrigger.trigger(ReportType.CAMT054C, ReportFrequency.DAILY))
                .thenReturn(execution);

        runner.triggerAllConfiguredReportTypes();

        assertThat(logAppender.list).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            String message = event.getFormattedMessage();
            assertThat(message).contains("configsRead=5").contains("messagesWritten=9");
        });
    }

    private static SchedulingProperties.Schedule schedule(String frequency, ReportType... reportTypes) {
        SchedulingProperties.Schedule schedule = new SchedulingProperties.Schedule();
        schedule.setFrequency(frequency);
        schedule.setReportTypes(List.of(reportTypes));
        return schedule;
    }

    private static JobExecution completedExecution() {
        JobExecution execution = new JobExecution(1L, new JobInstance(1L, "reportPipelineJob"), new JobParameters());
        execution.setStatus(BatchStatus.COMPLETED);
        return execution;
    }

    private static StepExecution stepExecution(
            JobExecution jobExecution, long stepExecutionId, long configsRead, long writeCount) {
        StepExecution step = new StepExecution(stepExecutionId, "reportPipelineStep", jobExecution);
        ExecutionContext context = new ExecutionContext();
        context.putLong(ReportPipelineItemReader.CONFIGS_READ_COUNT_KEY, configsRead);
        step.setExecutionContext(context);
        step.setWriteCount(writeCount);
        return step;
    }
}
