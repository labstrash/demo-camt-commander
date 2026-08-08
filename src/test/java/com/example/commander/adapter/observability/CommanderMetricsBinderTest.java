package com.example.commander.adapter.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.commander.adapter.message.MqCircuitBreaker;
import com.example.commander.port.DeadLetterMessageRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;

@ExtendWith(MockitoExtension.class)
class CommanderMetricsBinderTest {

    @Mock
    private DeadLetterMessageRepository deadLetterMessageRepository;

    @Mock
    private MqCircuitBreaker circuitBreaker;

    @Mock
    private Scheduler scheduler;

    private SimpleMeterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        new CommanderMetricsBinder(deadLetterMessageRepository, circuitBreaker, scheduler).bindTo(registry);
    }

    @Test
    void deadLetterBacklogGaugesReflectCurrentRepositoryCounts() {
        when(deadLetterMessageRepository.countByStatus("PENDING_RETRY")).thenReturn(3);
        when(deadLetterMessageRepository.countByStatus("FAILED")).thenReturn(1);

        assertThat(registry.get("commander.deadletter.backlog")
                        .tag("status", "PENDING_RETRY")
                        .gauge()
                        .value())
                .isEqualTo(3.0);
        assertThat(registry.get("commander.deadletter.backlog")
                        .tag("status", "FAILED")
                        .gauge()
                        .value())
                .isEqualTo(1.0);
    }

    @Test
    void circuitBreakerGaugeIsOneWhenRequestsAreAllowed() {
        when(circuitBreaker.isRequestAllowed()).thenReturn(true);

        assertThat(registry.get("commander.mq.circuit_breaker.up").gauge().value())
                .isEqualTo(1.0);
    }

    @Test
    void circuitBreakerGaugeIsZeroWhenOpen() {
        when(circuitBreaker.isRequestAllowed()).thenReturn(false);

        assertThat(registry.get("commander.mq.circuit_breaker.up").gauge().value())
                .isEqualTo(0.0);
    }

    @Test
    void quartzSchedulerGaugeIsOneWhenStarted() throws SchedulerException {
        when(scheduler.isStarted()).thenReturn(true);
        when(scheduler.isShutdown()).thenReturn(false);

        assertThat(registry.get("commander.quartz.scheduler.up").gauge().value())
                .isEqualTo(1.0);
    }

    @Test
    void quartzSchedulerGaugeIsZeroWhenNotStarted() throws SchedulerException {
        when(scheduler.isStarted()).thenReturn(false);

        assertThat(registry.get("commander.quartz.scheduler.up").gauge().value())
                .isEqualTo(0.0);
    }

    @Test
    void quartzSchedulerGaugeIsZeroWhenSchedulerThrows() throws SchedulerException {
        when(scheduler.isStarted()).thenThrow(new SchedulerException("boom"));

        assertThat(registry.get("commander.quartz.scheduler.up").gauge().value())
                .isEqualTo(0.0);
    }
}
