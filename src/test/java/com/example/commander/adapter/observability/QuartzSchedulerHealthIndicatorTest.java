package com.example.commander.adapter.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.springframework.boot.health.contributor.Status;

@ExtendWith(MockitoExtension.class)
class QuartzSchedulerHealthIndicatorTest {

    @Mock
    private Scheduler scheduler;

    private QuartzSchedulerHealthIndicator indicator;

    @BeforeEach
    void setUp() {
        indicator = new QuartzSchedulerHealthIndicator(scheduler);
    }

    @Test
    void reportsUpWhenStartedAndNotShutdown() throws SchedulerException {
        when(scheduler.isShutdown()).thenReturn(false);
        when(scheduler.isStarted()).thenReturn(true);

        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void reportsDownWhenNotYetStarted() throws SchedulerException {
        when(scheduler.isShutdown()).thenReturn(false);
        when(scheduler.isStarted()).thenReturn(false);

        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void reportsDownWhenShutDown() throws SchedulerException {
        when(scheduler.isShutdown()).thenReturn(true);

        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void reportsDownWhenTheSchedulerThrows() throws SchedulerException {
        when(scheduler.isShutdown()).thenThrow(new SchedulerException("boom"));

        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
    }
}
