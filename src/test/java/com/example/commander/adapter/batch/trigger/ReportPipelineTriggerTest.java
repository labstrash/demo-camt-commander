package com.example.commander.adapter.batch.trigger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.commander.domain.report.ReportFrequency;
import com.example.commander.domain.report.ReportWindow;
import com.example.commander.domain.report.ReportingPeriodCalculator;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobOperator;

@ExtendWith(MockitoExtension.class)
class ReportPipelineTriggerTest {

    private static final Instant WINDOW_START = Instant.parse("2026-07-27T06:00:00Z");
    private static final Instant WINDOW_END = Instant.parse("2026-07-28T06:00:00Z");

    @Mock
    private JobOperator jobOperator;

    @Mock
    private Job reportPipelineJob;

    @Mock
    private ReportingPeriodCalculator periodCalculator;

    @Mock
    private JobExecution jobExecution;

    private ReportPipelineTrigger trigger;

    @BeforeEach
    void setUp() {
        trigger = new ReportPipelineTrigger(jobOperator, reportPipelineJob, periodCalculator);
    }

    @Test
    void triggerWithExplicitReferenceInstantDerivesWindowAndLaunchesJobWithExpectedParameters() throws Exception {
        Instant referenceInstant = Instant.parse("2026-07-28T10:00:00Z");
        when(periodCalculator.calculateForReference(ReportFrequency.DAILY, referenceInstant, "CAMT054D"))
                .thenReturn(new ReportWindow(WINDOW_START, WINDOW_END));
        when(jobOperator.start(eq(reportPipelineJob), any(JobParameters.class))).thenReturn(jobExecution);

        JobExecution result = trigger.trigger("CAMT054D", ReportFrequency.DAILY, referenceInstant);

        assertThat(result).isSameAs(jobExecution);

        ArgumentCaptor<JobParameters> captor = ArgumentCaptor.forClass(JobParameters.class);
        verify(jobOperator).start(eq(reportPipelineJob), captor.capture());
        JobParameters jobParameters = captor.getValue();

        assertThat(jobParameters.getString("reportType")).isEqualTo("CAMT054D");
        assertThat(jobParameters.getString("reportFrequency")).isEqualTo("DAILY");
        assertThat(jobParameters.getParameter("windowStartUtc").value()).isEqualTo(WINDOW_START);
        assertThat(jobParameters.getParameter("windowEndUtc").value()).isEqualTo(WINDOW_END);
        assertThat(jobParameters.getParameter("triggeredAt").value()).isInstanceOf(Instant.class);
    }

    @Test
    void triggerWithoutReferenceInstantDerivesWindowFromApproximatelyNow() throws Exception {
        when(periodCalculator.calculateForReference(
                        eq(ReportFrequency.FOUR_TIMES_PER_DAY), any(Instant.class), eq("CAMT054C")))
                .thenReturn(new ReportWindow(WINDOW_START, WINDOW_END));
        when(jobOperator.start(eq(reportPipelineJob), any(JobParameters.class))).thenReturn(jobExecution);

        trigger.trigger("CAMT054C", ReportFrequency.FOUR_TIMES_PER_DAY);

        ArgumentCaptor<Instant> referenceInstantCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(periodCalculator)
                .calculateForReference(
                        eq(ReportFrequency.FOUR_TIMES_PER_DAY), referenceInstantCaptor.capture(), eq("CAMT054C"));

        assertThat(Duration.between(referenceInstantCaptor.getValue(), Instant.now())
                        .abs())
                .isLessThan(Duration.ofSeconds(5));
    }

    @Test
    void jobExecutionAlreadyRunningExceptionPropagatesUnchanged() throws Exception {
        when(periodCalculator.calculateForReference(any(), any(), any()))
                .thenReturn(new ReportWindow(WINDOW_START, WINDOW_END));
        when(jobOperator.start(eq(reportPipelineJob), any(JobParameters.class)))
                .thenThrow(new JobExecutionAlreadyRunningException("already running"));

        assertThatThrownBy(() -> trigger.trigger("CAMT054D", ReportFrequency.DAILY, Instant.now()))
                .isInstanceOf(JobExecutionAlreadyRunningException.class)
                .hasMessageContaining("already running");
    }

    @Test
    void successiveTriggersForTheSameWindowGetDistinctTriggeredAtValues() throws Exception {
        Instant referenceInstant = Instant.parse("2026-07-28T10:00:00Z");
        when(periodCalculator.calculateForReference(ReportFrequency.DAILY, referenceInstant, "CAMT054D"))
                .thenReturn(new ReportWindow(WINDOW_START, WINDOW_END));
        when(jobOperator.start(eq(reportPipelineJob), any(JobParameters.class))).thenReturn(jobExecution);

        Instant firstNow = Instant.now();
        Instant secondNow = firstNow.plus(Duration.ofSeconds(1));

        // ReportPipelineTrigger stamps triggeredAt via Instant.now(), not an injectable Clock
        // (deliberately, per the production design) — mock the static call itself so the two
        // stamps are deterministic, instead of relying on real wall-clock ticks (Thread.sleep).
        try (MockedStatic<Instant> instantMock = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            instantMock.when(Instant::now).thenReturn(firstNow, secondNow);

            trigger.trigger("CAMT054D", ReportFrequency.DAILY, referenceInstant);
            trigger.trigger("CAMT054D", ReportFrequency.DAILY, referenceInstant);
        }

        ArgumentCaptor<JobParameters> captor = ArgumentCaptor.forClass(JobParameters.class);
        verify(jobOperator, times(2)).start(eq(reportPipelineJob), captor.capture());

        Instant firstTriggeredAt = (Instant)
                captor.getAllValues().get(0).getParameter("triggeredAt").value();
        Instant secondTriggeredAt = (Instant)
                captor.getAllValues().get(1).getParameter("triggeredAt").value();

        assertThat(firstTriggeredAt).isEqualTo(firstNow);
        assertThat(secondTriggeredAt).isEqualTo(secondNow);
    }
}
