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

import com.example.commander.domain.message.ReportType;
import com.example.commander.domain.report.ReportFrequency;
import com.example.commander.domain.report.ReportWindow;
import com.example.commander.domain.report.ReportingPeriodCalculator;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
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
        when(periodCalculator.calculateForReference(ReportFrequency.DAILY, referenceInstant, ReportType.CAMT054D))
                .thenReturn(new ReportWindow(WINDOW_START, WINDOW_END));
        when(jobOperator.start(eq(reportPipelineJob), any(JobParameters.class))).thenReturn(jobExecution);

        JobExecution result = trigger.trigger(ReportType.CAMT054D, ReportFrequency.DAILY, referenceInstant);

        assertThat(result).isSameAs(jobExecution);

        ArgumentCaptor<JobParameters> captor = ArgumentCaptor.forClass(JobParameters.class);
        verify(jobOperator).start(eq(reportPipelineJob), captor.capture());
        JobParameters jobParameters = captor.getValue();

        assertThat(jobParameters.getString("reportType")).isEqualTo("CAMT054D");
        assertThat(jobParameters.getString("reportFrequency")).isEqualTo("DAILY");
        assertThat(jobParameters.getParameter("startDateTimeUtc").value()).isEqualTo(WINDOW_START);
        assertThat(jobParameters.getParameter("endDateTimeUtc").value()).isEqualTo(WINDOW_END);
        assertThat(jobParameters.getParameter("triggeredAt").value()).isInstanceOf(Instant.class);
    }

    @Test
    void triggerWithoutReferenceInstantDerivesWindowFromApproximatelyNow() throws Exception {
        when(periodCalculator.calculateForReference(
                        eq(ReportFrequency.FOUR_TIMES_PER_DAY), any(Instant.class), eq(ReportType.CAMT054C)))
                .thenReturn(new ReportWindow(WINDOW_START, WINDOW_END));
        when(jobOperator.start(eq(reportPipelineJob), any(JobParameters.class))).thenReturn(jobExecution);

        trigger.trigger(ReportType.CAMT054C, ReportFrequency.FOUR_TIMES_PER_DAY);

        ArgumentCaptor<Instant> referenceInstantCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(periodCalculator)
                .calculateForReference(
                        eq(ReportFrequency.FOUR_TIMES_PER_DAY),
                        referenceInstantCaptor.capture(),
                        eq(ReportType.CAMT054C));

        assertThat(Duration.between(referenceInstantCaptor.getValue(), Instant.now())
                        .abs())
                .isLessThan(Duration.ofSeconds(5));
    }

    @Test
    void triggerWithFireTimeAndSequenceDerivesWindowViaPlainCalculateAndLaunchesJob() throws Exception {
        Instant fireTime = Instant.parse("2026-07-28T13:00:00Z");
        List<LocalTime> boundaries = List.of(LocalTime.of(10, 0), LocalTime.of(13, 0));
        when(periodCalculator.calculate(ReportFrequency.FOUR_TIMES_PER_DAY, fireTime, 1, boundaries))
                .thenReturn(new ReportWindow(WINDOW_START, WINDOW_END));
        when(jobOperator.start(eq(reportPipelineJob), any(JobParameters.class))).thenReturn(jobExecution);

        JobExecution result =
                trigger.trigger(ReportType.CAMT054C, ReportFrequency.FOUR_TIMES_PER_DAY, fireTime, 1, boundaries);

        assertThat(result).isSameAs(jobExecution);

        ArgumentCaptor<JobParameters> captor = ArgumentCaptor.forClass(JobParameters.class);
        verify(jobOperator).start(eq(reportPipelineJob), captor.capture());
        JobParameters jobParameters = captor.getValue();

        assertThat(jobParameters.getString("reportType")).isEqualTo("CAMT054C");
        assertThat(jobParameters.getString("reportFrequency")).isEqualTo("FOUR_TIMES_PER_DAY");
        assertThat(jobParameters.getParameter("startDateTimeUtc").value()).isEqualTo(WINDOW_START);
        assertThat(jobParameters.getParameter("endDateTimeUtc").value()).isEqualTo(WINDOW_END);
        // No triggeredAt - see next test for why this matters.
        assertThat(jobParameters.getParameter("triggeredAt")).isNull();
    }

    @Test
    void repeatedQuartzTriggersForTheSameWindowProduceIdenticalJobParameters() throws Exception {
        // Unlike the reference-instant overloads (see successiveTriggersForTheSameWindowGetDistinctTriggeredAtValues
        // above), the Quartz-originated overload must NOT stamp triggeredAt: Spring Batch's own
        // "refuse to relaunch an already-completed JobInstance" guard only works if two calls
        // for the same window produce byte-for-byte identical JobParameters - that's exactly
        // the case this overload exists to preserve, since a Quartz misfire recovery can
        // legitimately re-fire an already-completed window.
        Instant fireTime = Instant.parse("2026-07-28T13:00:00Z");
        when(periodCalculator.calculate(ReportFrequency.DAILY, fireTime, null, null))
                .thenReturn(new ReportWindow(WINDOW_START, WINDOW_END));
        when(jobOperator.start(eq(reportPipelineJob), any(JobParameters.class))).thenReturn(jobExecution);

        trigger.trigger(ReportType.CAMT054D, ReportFrequency.DAILY, fireTime, null, null);
        trigger.trigger(ReportType.CAMT054D, ReportFrequency.DAILY, fireTime, null, null);

        ArgumentCaptor<JobParameters> captor = ArgumentCaptor.forClass(JobParameters.class);
        verify(jobOperator, times(2)).start(eq(reportPipelineJob), captor.capture());
        assertThat(captor.getAllValues().get(0)).isEqualTo(captor.getAllValues().get(1));
    }

    @Test
    void triggerWithFireTimeAndNullSequenceBoundariesWorksForNonWindowTimeFrequencies() throws Exception {
        Instant fireTime = Instant.parse("2026-07-28T06:00:00Z");
        when(periodCalculator.calculate(ReportFrequency.DAILY, fireTime, null, null))
                .thenReturn(new ReportWindow(WINDOW_START, WINDOW_END));
        when(jobOperator.start(eq(reportPipelineJob), any(JobParameters.class))).thenReturn(jobExecution);

        JobExecution result = trigger.trigger(ReportType.CAMT054D, ReportFrequency.DAILY, fireTime, null, null);

        assertThat(result).isSameAs(jobExecution);
        verify(periodCalculator).calculate(ReportFrequency.DAILY, fireTime, null, null);
    }

    @Test
    void jobExecutionAlreadyRunningExceptionPropagatesUnchanged() throws Exception {
        when(periodCalculator.calculateForReference(any(), any(), any()))
                .thenReturn(new ReportWindow(WINDOW_START, WINDOW_END));
        when(jobOperator.start(eq(reportPipelineJob), any(JobParameters.class)))
                .thenThrow(new JobExecutionAlreadyRunningException("already running"));

        assertThatThrownBy(() -> trigger.trigger(ReportType.CAMT054D, ReportFrequency.DAILY, Instant.now()))
                .isInstanceOf(JobExecutionAlreadyRunningException.class)
                .hasMessageContaining("already running");
    }

    @Test
    void successiveTriggersForTheSameWindowGetDistinctTriggeredAtValues() throws Exception {
        Instant referenceInstant = Instant.parse("2026-07-28T10:00:00Z");
        when(periodCalculator.calculateForReference(ReportFrequency.DAILY, referenceInstant, ReportType.CAMT054D))
                .thenReturn(new ReportWindow(WINDOW_START, WINDOW_END));
        when(jobOperator.start(eq(reportPipelineJob), any(JobParameters.class))).thenReturn(jobExecution);

        Instant firstNow = Instant.now();
        Instant secondNow = firstNow.plus(Duration.ofSeconds(1));

        // ReportPipelineTrigger stamps triggeredAt via Instant.now(), not an injectable Clock
        // (deliberately, per the production design) — mock the static call itself so the two
        // stamps are deterministic, instead of relying on real wall-clock ticks (Thread.sleep).
        try (MockedStatic<Instant> instantMock = mockStatic(Instant.class, CALLS_REAL_METHODS)) {
            instantMock.when(Instant::now).thenReturn(firstNow, secondNow);

            trigger.trigger(ReportType.CAMT054D, ReportFrequency.DAILY, referenceInstant);
            trigger.trigger(ReportType.CAMT054D, ReportFrequency.DAILY, referenceInstant);
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
