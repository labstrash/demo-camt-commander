package com.example.commander.adapter.batch.processor;

import com.example.commander.domain.config.RecipientRow;
import com.example.commander.domain.message.Recipient;
import com.example.commander.domain.message.RecipientType;
import com.example.commander.domain.message.ReportMessage;
import com.example.commander.domain.message.ReportMessageEnvelope;
import com.example.commander.port.ReportConfigRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

/**
 * Resolves the real recipient for each pipeline message, replacing the reader's
 * {@code UNRESOLVED} placeholder (see {@code ReportPipelineItemReader.contextFor()}).
 *
 * <p>The lookup key travels on {@link ReportMessageEnvelope#recipientId()} — not on {@link
 * Recipient} itself, which ends up inside {@link ReportMessage} and is serialized onto MQ —
 * this processor reads it back out, looks up the real recipient, and rebuilds the message
 * with every other field (including {@code correlationId}/{@code id}, which must never be
 * regenerated — see {@code FanOutAssemblyService}) copied forward unchanged. The rebuilt
 * envelope drops {@code recipientId}: it's served its purpose once resolved, and mustn't be
 * threaded any further since {@code ReportMessageDeliveryService} is the last place before
 * MQ that has an opportunity to catch anything that shouldn't go out.
 *
 * <p>An unresolvable recipient ID filters the message out of the chunk entirely (return
 * {@code null}), per {@code ItemProcessor}'s contract — the filter event is logged with
 * enough detail (config ID, recipient ID) to investigate.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class RecipientResolvingReportMessageProcessor
        implements ItemProcessor<ReportMessageEnvelope, ReportMessageEnvelope> {

    private final ReportConfigRepository repository;

    @Override
    public ReportMessageEnvelope process(ReportMessageEnvelope item) {
        ReportMessage payload = item.payload();
        Long recipientId = item.recipientId();

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
                .recipient(new Recipient(RecipientType.valueOf(row.type()), row.value(), row.name()))
                .build();

        return new ReportMessageEnvelope(resolved, item.configId(), item.scopeId());
    }
}
