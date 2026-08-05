package com.example.commander.domain.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReportMessageEnvelopeTest {

    @Test
    void bundledPayloadWithNullScopeIdIsValid() {
        ReportMessageEnvelope envelope = new ReportMessageEnvelope(payload(true), 1L, null);

        assertThat(envelope.isBundled()).isTrue();
        assertThat(envelope.isSingleScope()).isFalse();
    }

    @Test
    void unbundledPayloadWithNonNullScopeIdIsValid() {
        ReportMessageEnvelope envelope = new ReportMessageEnvelope(payload(false), 1L, 101L);

        assertThat(envelope.isBundled()).isFalse();
        assertThat(envelope.isSingleScope()).isTrue();
    }

    @Test
    void bundledPayloadWithNonNullScopeIdViolatesTheInvariant() {
        assertThatThrownBy(() -> new ReportMessageEnvelope(payload(true), 1L, 101L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bundled=true");
    }

    @Test
    void unbundledPayloadWithNullScopeIdViolatesTheInvariant() {
        assertThatThrownBy(() -> new ReportMessageEnvelope(payload(false), 1L, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bundled=false");
    }

    @Test
    void rejectsNonPositiveConfigId() {
        assertThatThrownBy(() -> new ReportMessageEnvelope(payload(true), 0L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void rejectsNullPayload() {
        assertThatThrownBy(() -> new ReportMessageEnvelope(null, 1L, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void configOnlyMessageIsDetectedFromAnEmptyPaymentTypeAllocationsList() {
        ReportMessageEnvelope envelope = new ReportMessageEnvelope(payload(true), 1L, null);

        assertThat(envelope.isConfigOnly()).isTrue();
    }

    private static ReportMessage payload(boolean bundled) {
        return ReportMessage.builder()
                .configId(12345678)
                .reportType(ReportType.CAMT054C)
                .reportVersion("1.0")
                .windowStartUtc(Instant.parse("2026-07-01T00:00:00Z"))
                .windowEndUtc(Instant.parse("2026-07-02T00:00:00Z"))
                .bundled(bundled)
                .triggerType(TriggerType.SCHEDULED)
                .recipient(new Recipient(999L, RecipientType.BIC, "SOMEBIC", "Some Recipient"))
                .paymentTypeGroups(List.of())
                .correlationId("corr-id")
                .messageId("FIKASE054C123450Q9Z6XZHPAH5R0000")
                .build();
    }
}
