package com.example.commander.domain.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.commander.domain.report.ReportWindow;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ReportContextTest {

    private static final Instant START = Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant END = Instant.parse("2026-07-02T00:00:00Z");
    private static final ReportWindow WINDOW = new ReportWindow(START, END);

    @Test
    void constructsWithValidFields() {
        ReportContext context = new ReportContext(WINDOW, "1.0", TriggerType.SCHEDULED);

        assertThat(context.window()).isEqualTo(WINDOW);
        assertThat(context.reportVersion()).isEqualTo("1.0");
        assertThat(context.triggerType()).isEqualTo(TriggerType.SCHEDULED);
    }

    @Test
    void rejectsNullWindow() {
        assertThatThrownBy(() -> new ReportContext(null, "1.0", TriggerType.SCHEDULED))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullReportVersion() {
        assertThatThrownBy(() -> new ReportContext(WINDOW, null, TriggerType.SCHEDULED))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullTriggerType() {
        assertThatThrownBy(() -> new ReportContext(WINDOW, "1.0", null)).isInstanceOf(NullPointerException.class);
    }
}
