package com.example.commander.adapter.batch.processor;

import com.example.commander.domain.config.RecipientRow;
import com.example.commander.domain.message.Recipient;
import com.example.commander.domain.message.RecipientType;
import com.example.commander.domain.message.ReportMessage;
import com.example.commander.domain.message.ReportMessageEnvelope;
import com.example.commander.repository.ReportConfigRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

/**
 * Resolves the real recipient for each pipeline message, replacing the reader's
 * {@code UNRESOLVED} placeholder (see {@code ReportPipelineItemReader.contextFor()}).
 *
 * <p>The placeholder {@link Recipient} stashes {@code config.messageRecipientId()} in its
 * {@code id} field — this processor reads it back out, looks up the real recipient, and
 * rebuilds the message with every other field (including {@code correlationId}/{@code id},
 * which must never be regenerated — see {@code FanOutAssemblyService}) copied forward
 * unchanged.
 *
 * <p>An unresolvable recipient ID filters the message out of the chunk entirely (return
 * {@code null}), per {@code ItemProcessor}'s contract — the filter event is logged with
 * enough detail (config ID, recipient ID) to investigate.
 */
@Component
public class RecipientResolvingReportMessageProcessor
        implements ItemProcessor<ReportMessageEnvelope, ReportMessageEnvelope> {

    private static final Logger log = LoggerFactory.getLogger(RecipientResolvingReportMessageProcessor.class);

    private final ReportConfigRepository repository;

    public RecipientResolvingReportMessageProcessor(ReportConfigRepository repository) {
        this.repository = repository;
    }

    @Override
    public ReportMessageEnvelope process(ReportMessageEnvelope item) {
        ReportMessage payload = item.payload();
        long recipientId = payload.recipient().id();

        Optional<RecipientRow> recipient = repository.findRecipientById(recipientId);
        if (recipient.isEmpty()) {
            log.warn(
                    "Filtering message: unresolvable recipient. reportConfigId={}, reportId={}, recipientId={}",
                    item.configId(),
                    payload.reportId(),
                    recipientId);
            return null;
        }

        RecipientRow row = recipient.get();
        ReportMessage resolved = ReportMessage.builder()
                .from(payload)
                .recipient(new Recipient(row.id(), RecipientType.valueOf(row.type()), row.value(), row.name()))
                .build();

        return new ReportMessageEnvelope(resolved, item.configId(), item.scopeId());
    }
}
