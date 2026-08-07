package com.example.commander.adapter.batch.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.commander.domain.config.RecipientRow;
import com.example.commander.domain.message.*;
import com.example.commander.repository.ReportConfigRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecipientResolvingReportMessageProcessorTest {

    @Mock
    private ReportConfigRepository repository;

    private RecipientResolvingReportMessageProcessor processor;

    @Test
    void resolvesRecipientAndRebuildsMessageWithEveryOtherFieldUnchanged() throws Exception {
        processor = new RecipientResolvingReportMessageProcessor(repository);
        RecipientRow recipient = new RecipientRow(999L, "BIC", "SOMEBIC", "Some Recipient");
        when(repository.findRecipientById(999L)).thenReturn(Optional.of(recipient));

        ReportMessageEnvelope item = placeholderMessage();

        ReportMessageEnvelope result = processor.process(item);

        assertThat(result).isNotNull();
        assertThat(result.payload().recipient())
                .isEqualTo(new Recipient(999L, RecipientType.BIC, "SOMEBIC", "Some Recipient"));
        assertThat(result.configId()).isEqualTo(item.configId());
        assertThat(result.scopeId()).isEqualTo(item.scopeId());
        // messageId is non-deterministic — must be threaded forward unchanged, never regenerated
        assertThat(result.payload().messageId()).isEqualTo(item.payload().messageId());
        assertThat(result.payload().correlationId()).isEqualTo(item.payload().correlationId());
        assertThat(result.payload().reportId()).isEqualTo(item.payload().reportId());
        assertThat(result.payload().type()).isEqualTo(item.payload().type());
        assertThat(result.payload().paymentTypes()).isEqualTo(item.payload().paymentTypes());
    }

    @Test
    void filtersMessageWhenRecipientIsUnresolvable() throws Exception {
        processor = new RecipientResolvingReportMessageProcessor(repository);
        when(repository.findRecipientById(999L)).thenReturn(Optional.empty());

        ReportMessageEnvelope result = processor.process(placeholderMessage());

        assertThat(result).isNull();
    }

    private static ReportMessageEnvelope placeholderMessage() {
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
                new Recipient(999L, RecipientType.SIGNER_ID, "UNRESOLVED", "UNRESOLVED"),
                List.of(),
                null,
                "corr-id",
                "FIKASE054C123450Q9Z6XZHPAH5R0000");
        return new ReportMessageEnvelope(payload, 1L, null);
    }
}
