package com.example.commander.domain.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Covers {@link ReportMessage}'s own invariants — its compact constructor's validation and
 * {@link ReportMessage.Builder}'s required-field checks — independent of any of the many
 * services that happen to construct one. The two aren't interchangeable to test: {@code
 * Builder.build()} throws its own {@link IllegalStateException} before ever reaching the
 * canonical constructor, so exercising the record's own {@code Objects.requireNonNull} calls
 * needs the canonical constructor called directly, bypassing the builder.
 */
class ReportMessageTest {

    private static final Instant START = Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant END = Instant.parse("2026-07-02T00:00:00Z");
    private static final Recipient RECIPIENT = new Recipient(999L, RecipientType.BIC, "SOMEBIC", "Some Recipient");

    @Test
    void rejectsNonPositiveReportId() {
        assertThatThrownBy(() -> new ReportMessage(
                        0,
                        ReportType.CAMT054C,
                        "1.0",
                        START,
                        END,
                        true,
                        "IBAN",
                        false,
                        false,
                        TriggerType.SCHEDULED,
                        RECIPIENT,
                        List.of(),
                        null,
                        "corr-id",
                        "msg-id"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reportId must be positive");
    }

    @Test
    void rejectsStartAfterEnd() {
        assertThatThrownBy(() -> canonical(
                        ReportType.CAMT054C, "1.0", END, START, TriggerType.SCHEDULED, RECIPIENT, "corr-id", "msg-id"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("startDateTimeUtc must be before endDateTimeUtc");
    }

    @Test
    void allowsStartEqualToEnd() {
        ReportMessage message = canonical(
                ReportType.CAMT054C, "1.0", START, START, TriggerType.SCHEDULED, RECIPIENT, "corr-id", "msg-id");

        assertThat(message.startDateTimeUtc()).isEqualTo(message.endDateTimeUtc());
    }

    @Test
    void rejectsNullType() {
        assertThatThrownBy(
                        () -> canonical(null, "1.0", START, END, TriggerType.SCHEDULED, RECIPIENT, "corr-id", "msg-id"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("type");
    }

    @Test
    void rejectsNullVersion() {
        assertThatThrownBy(() -> canonical(
                        ReportType.CAMT054C, null, START, END, TriggerType.SCHEDULED, RECIPIENT, "corr-id", "msg-id"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("version");
    }

    @Test
    void rejectsNullStartDateTimeUtc() {
        assertThatThrownBy(() -> canonical(
                        ReportType.CAMT054C, "1.0", null, END, TriggerType.SCHEDULED, RECIPIENT, "corr-id", "msg-id"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("startDateTimeUtc");
    }

    @Test
    void rejectsNullEndDateTimeUtc() {
        assertThatThrownBy(() -> canonical(
                        ReportType.CAMT054C, "1.0", START, null, TriggerType.SCHEDULED, RECIPIENT, "corr-id", "msg-id"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("endDateTimeUtc");
    }

    @Test
    void rejectsNullTriggerType() {
        assertThatThrownBy(
                        () -> canonical(ReportType.CAMT054C, "1.0", START, END, null, RECIPIENT, "corr-id", "msg-id"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("triggerType");
    }

    @Test
    void rejectsNullRecipient() {
        assertThatThrownBy(() -> canonical(
                        ReportType.CAMT054C, "1.0", START, END, TriggerType.SCHEDULED, null, "corr-id", "msg-id"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("recipient");
    }

    @Test
    void rejectsNullCorrelationId() {
        assertThatThrownBy(() -> canonical(
                        ReportType.CAMT054C, "1.0", START, END, TriggerType.SCHEDULED, RECIPIENT, null, "msg-id"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("correlationId");
    }

    @Test
    void rejectsNullMessageId() {
        assertThatThrownBy(() -> canonical(
                        ReportType.CAMT054C, "1.0", START, END, TriggerType.SCHEDULED, RECIPIENT, "corr-id", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("messageId");
    }

    @Test
    void nullPaymentTypesDefaultsToAnEmptyList() {
        ReportMessage message = new ReportMessage(
                1,
                ReportType.CAMT054C,
                "1.0",
                START,
                END,
                true,
                "IBAN",
                false,
                false,
                TriggerType.SCHEDULED,
                RECIPIENT,
                null,
                null,
                "corr-id",
                "msg-id");

        assertThat(message.paymentTypes()).isEmpty();
        assertThat(message.hasNoPaymentTypes()).isTrue();
        assertThat(message.hasPaymentTypes()).isFalse();
    }

    @Test
    void paymentTypesListIsDefensivelyCopiedAndImmutable() {
        List<PaymentTypeAllocation> mutableSource = new ArrayList<>();
        mutableSource.add(new PaymentTypeAllocation("SWISH", List.of(), List.of()));
        ReportMessage message = new ReportMessage(
                1,
                ReportType.CAMT054C,
                "1.0",
                START,
                END,
                true,
                "IBAN",
                false,
                false,
                TriggerType.SCHEDULED,
                RECIPIENT,
                mutableSource,
                null,
                "corr-id",
                "msg-id");
        mutableSource.add(new PaymentTypeAllocation("BG", List.of(), List.of()));

        assertThat(message.paymentTypes()).hasSize(1);
        assertThatThrownBy(() -> message.paymentTypes().add(new PaymentTypeAllocation("BG", List.of(), List.of())))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void paymentTypeCountAndTotalAccountAssignmentsCountAcrossEveryAllocation() {
        PaymentTypeAllocation swish = new PaymentTypeAllocation(
                "SWISH",
                List.of(
                        new AccountAllocation("3300", "1234567", "33001234567", "SEK"),
                        new AccountAllocation("3300", "7654321", "33007654321", "SEK")),
                List.of());
        PaymentTypeAllocation bg = new PaymentTypeAllocation(
                "BG", List.of(new AccountAllocation("3300", "1111111", "33001111111", "SEK")), List.of());
        ReportMessage message = new ReportMessage(
                1,
                ReportType.CAMT054C,
                "1.0",
                START,
                END,
                true,
                "IBAN",
                false,
                false,
                TriggerType.SCHEDULED,
                RECIPIENT,
                List.of(swish, bg),
                null,
                "corr-id",
                "msg-id");

        assertThat(message.paymentTypeCount()).isEqualTo(2);
        assertThat(message.totalAccountAssignments()).isEqualTo(3);
        assertThat(message.hasPaymentTypes()).isTrue();
        assertThat(message.hasNoPaymentTypes()).isFalse();
    }

    @Test
    void builderRequiresEveryMandatoryFieldBeforeBuilding() {
        assertThatThrownBy(() -> ReportMessage.builder().build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("reportId is required");

        assertThatThrownBy(() -> validBuilder().type(null).build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("type is required");

        assertThatThrownBy(() -> validBuilder().correlationId(null).build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("correlationId is required");
    }

    @Test
    void builderFromRoundTripsEveryFieldOfAnExistingMessage() {
        ReportMessage original = validBuilder().build();

        ReportMessage rebuilt = ReportMessage.builder().from(original).build();

        assertThat(rebuilt).isEqualTo(original);
    }

    private static ReportMessage canonical(
            ReportType type,
            String version,
            Instant start,
            Instant end,
            TriggerType triggerType,
            Recipient recipient,
            String correlationId,
            String messageId) {
        return new ReportMessage(
                1,
                type,
                version,
                start,
                end,
                true,
                "IBAN",
                false,
                false,
                triggerType,
                recipient,
                List.of(),
                null,
                correlationId,
                messageId);
    }

    private static ReportMessage.Builder validBuilder() {
        return ReportMessage.builder()
                .reportId(12345678)
                .type(ReportType.CAMT054C)
                .version("1.0")
                .windowStartUtc(START)
                .windowEndUtc(END)
                .bundled(true)
                .accountFormat("IBAN")
                .isPaginated(false)
                .isEmptyReportAllowed(false)
                .triggerType(TriggerType.SCHEDULED)
                .recipient(RECIPIENT)
                .paymentTypeGroups(List.of())
                .requestorName("alice")
                .correlationId("corr-id")
                .messageId("msg-id");
    }
}
