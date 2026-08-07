package com.example.commander.adapter.batch.writer;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.commander.domain.message.Recipient;
import com.example.commander.domain.message.RecipientType;
import com.example.commander.domain.message.ReportMessage;
import com.example.commander.domain.message.ReportMessageEnvelope;
import com.example.commander.domain.message.ReportType;
import com.example.commander.domain.message.TriggerType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.Chunk;

class LoggingReportMessageWriterTest {

    private final LoggingReportMessageWriter writer = new LoggingReportMessageWriter();

    @Test
    void writesAChunkOfMessagesWithoutThrowing() {
        ReportMessage message = new ReportMessage(
                12345678,
                ReportType.CAMT054C,
                "1.0",
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-07-02T00:00:00Z"),
                false,
                "IBAN",
                true,
                true,
                TriggerType.SCHEDULED,
                new Recipient(999L, RecipientType.SIGNER_ID, "UNRESOLVED", "UNRESOLVED"),
                List.of(),
                null,
                "corr-id",
                "FIKASE054C123450Q9Z6XZHPAH5R0000");
        ReportMessageEnvelope envelope = new ReportMessageEnvelope(message, 1L, 101L);

        assertThatCode(() -> writer.write(new Chunk<>(envelope))).doesNotThrowAnyException();
    }

    @Test
    void writesAnEmptyChunkWithoutThrowing() {
        assertThatCode(() -> writer.write(new Chunk<>())).doesNotThrowAnyException();
    }
}
