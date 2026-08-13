package com.example.commander.adapter.batch.writer;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.commander.adapter.message.MqProperties;
import com.example.commander.application.ReportMessageDeliveryService;
import com.example.commander.domain.audit.ReportCommandAuditStatus;
import com.example.commander.domain.message.Recipient;
import com.example.commander.domain.message.RecipientType;
import com.example.commander.domain.message.ReportMessage;
import com.example.commander.domain.message.ReportMessageEnvelope;
import com.example.commander.domain.message.ReportType;
import com.example.commander.domain.message.TriggerType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.infrastructure.item.Chunk;

/**
 * This is a thin {@code @StepScope} adapter — the send/dead-letter/audit behavior itself is
 * {@link ReportMessageDeliveryService}'s concern, covered by {@code
 * ReportMessageDeliveryServiceTest}. What's left to verify here is only that this writer
 * resolves its batch-specific context correctly and delegates once per chunk item with it.
 */
@ExtendWith(MockitoExtension.class)
class MqReportMessageWriterTest {

    private static final String TARGET_QUEUE = "CAMT.054C.QUEUE";

    @Mock
    private ReportMessageDeliveryService deliveryService;

    private MqReportMessageWriter newWriter() {
        MqProperties mqProperties = new MqProperties();
        mqProperties.setQueues(Map.of(ReportType.CAMT054C, TARGET_QUEUE));
        return new MqReportMessageWriter(deliveryService, mqProperties, "CAMT054C", "DAILY", 111L, 222L);
    }

    @Test
    void delegatesEachItemToTheDeliveryServiceWithTheResolvedBatchContext() throws Exception {
        MqReportMessageWriter writer = newWriter();
        ReportMessageEnvelope item = message();
        when(deliveryService.deliver(item, TARGET_QUEUE, "DAILY", 111L, 222L))
                .thenReturn(ReportCommandAuditStatus.SENT);

        writer.write(new Chunk<>(item));

        verify(deliveryService).deliver(item, TARGET_QUEUE, "DAILY", 111L, 222L);
    }

    @Test
    void delegatesEveryItemInAMultiItemChunk() throws Exception {
        MqReportMessageWriter writer = newWriter();
        ReportMessageEnvelope first = message("corr-1", "FIKASE054C123450Q9Z6XZHPAH5R0000");
        ReportMessageEnvelope second = message("corr-2", "FIKASE054C123450Q9Z6XZHPAH5R0001");
        when(deliveryService.deliver(eq(first), eq(TARGET_QUEUE), eq("DAILY"), eq(111L), eq(222L)))
                .thenReturn(ReportCommandAuditStatus.SENT);
        when(deliveryService.deliver(eq(second), eq(TARGET_QUEUE), eq("DAILY"), eq(111L), eq(222L)))
                .thenReturn(ReportCommandAuditStatus.FAILED);

        writer.write(new Chunk<>(List.of(first, second)));

        verify(deliveryService).deliver(first, TARGET_QUEUE, "DAILY", 111L, 222L);
        verify(deliveryService).deliver(second, TARGET_QUEUE, "DAILY", 111L, 222L);
    }

    private static ReportMessageEnvelope message() {
        return message("corr-id", "FIKASE054C123450Q9Z6XZHPAH5R0000");
    }

    private static ReportMessageEnvelope message(String correlationId, String id) {
        ReportMessage payload = new ReportMessage(
                12345678,
                ReportType.CAMT054C,
                "1.0",
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-07-02T00:00:00Z"),
                true,
                "IBAN",
                true,
                true,
                TriggerType.SCHEDULED,
                new Recipient(RecipientType.BIC, "SOMEBIC", "Some Recipient"),
                List.of(),
                null,
                correlationId,
                id);
        return new ReportMessageEnvelope(payload, 1L, null);
    }
}
