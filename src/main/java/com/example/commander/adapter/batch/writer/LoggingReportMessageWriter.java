package com.example.commander.adapter.batch.writer;

import com.example.commander.domain.message.ReportMessage;
import com.example.commander.domain.message.ReportMessageEnvelope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;

/**
 * Logging-only writer: the fallback delivery path when sending report messages to MQ is
 * disabled via feature flag, and a debug aid for inspecting payloads at DEBUG level even when
 * real delivery is active. Unwraps each {@link ReportMessageEnvelope} to its wire payload for
 * the log output; the envelope's internal {@code configId}/{@code scopeId} bookkeeping is not
 * needed here.
 */
@Slf4j
@Component
public class LoggingReportMessageWriter implements ItemWriter<ReportMessageEnvelope> {

    @Override
    public void write(Chunk<? extends ReportMessageEnvelope> chunk) {
        log.info("Writing chunk of {} outbound report messages", chunk.size());
        for (ReportMessageEnvelope item : chunk) {
            ReportMessage message = item.payload();
            log.debug("Report Message={}", message);
        }
    }
}
