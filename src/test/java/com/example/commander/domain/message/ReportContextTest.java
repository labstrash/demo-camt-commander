package com.example.commander.domain.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ReportContextTest {

    private static final Instant START = Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant END = Instant.parse("2026-07-02T00:00:00Z");

    @Test
    void constructsWithValidFields() {
        ReportContext context = new ReportContext(START, END, "1.0", TriggerType.SCHEDULED);

        assertThat(context.windowStartUtc()).isEqualTo(START);
        assertThat(context.windowEndUtc()).isEqualTo(END);
        assertThat(context.reportVersion()).isEqualTo("1.0");
        assertThat(context.triggerType()).isEqualTo(TriggerType.SCHEDULED);
    }

    @Test
    void rejectsWindowStartAfterWindowEnd() {
        assertThatThrownBy(() -> new ReportContext(END, START, "1.0", TriggerType.SCHEDULED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be before");
    }

    @Test
    void rejectsNullWindowStart() {
        assertThatThrownBy(() -> new ReportContext(null, END, "1.0", TriggerType.SCHEDULED))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullReportVersion() {
        assertThatThrownBy(() -> new ReportContext(START, END, null, TriggerType.SCHEDULED))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullTriggerType() {
        assertThatThrownBy(() -> new ReportContext(START, END, "1.0", null)).isInstanceOf(NullPointerException.class);
    }
}
