package com.example.commander.adapter.batch.writer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.commander.adapter.message.MqProperties;
import com.example.commander.domain.message.Recipient;
import com.example.commander.domain.message.RecipientType;
import com.example.commander.domain.message.ReportMessage;
import com.example.commander.domain.message.ReportMessageEnvelope;
import com.example.commander.domain.message.ReportType;
import com.example.commander.domain.message.TriggerType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.infrastructure.item.Chunk;

@ExtendWith(MockitoExtension.class)
class CompositeReportMessageWriterTest {

    @Mock
    private LoggingReportMessageWriter loggingWriter;

    @Mock
    private MqReportMessageWriter mqWriter;

    @Test
    void mqEnabledWritesToBothWritersLoggingFirst() throws Exception {
        MqProperties mqProperties = new MqProperties();
        mqProperties.setEnabled(true);
        CompositeReportMessageWriter writer = new CompositeReportMessageWriter(loggingWriter, mqWriter, mqProperties);

        writer.write(new Chunk<>(message()));

        InOrder order = inOrder(loggingWriter, mqWriter);
        order.verify(loggingWriter).write(any());
        order.verify(mqWriter).write(any());
    }

    @Test
    void mqDisabledWritesOnlyToTheLoggingWriter() throws Exception {
        MqProperties mqProperties = new MqProperties();
        mqProperties.setEnabled(false);
        CompositeReportMessageWriter writer = new CompositeReportMessageWriter(loggingWriter, mqWriter, mqProperties);

        writer.write(new Chunk<>(message()));

        verify(loggingWriter).write(any());
        verifyNoInteractions(mqWriter);
    }

    private static ReportMessageEnvelope message() {
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
                "corr-id",
                "FIKASE054C123450Q9Z6XZHPAH5R0000");
        return new ReportMessageEnvelope(payload, 1L, null);
    }
}
