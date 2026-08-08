package com.example.commander.adapter.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.commander.adapter.message.MqCircuitBreaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Status;

@ExtendWith(MockitoExtension.class)
class MqCircuitBreakerHealthIndicatorTest {

    @Mock
    private MqCircuitBreaker circuitBreaker;

    private MqCircuitBreakerHealthIndicator indicator;

    @BeforeEach
    void setUp() {
        indicator = new MqCircuitBreakerHealthIndicator(circuitBreaker);
    }

    @Test
    void reportsUpWhenTheBreakerIsAllowingRequests() {
        when(circuitBreaker.isRequestAllowed()).thenReturn(true);

        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void reportsDownWhenTheBreakerIsOpen() {
        when(circuitBreaker.isRequestAllowed()).thenReturn(false);

        assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
    }
}
