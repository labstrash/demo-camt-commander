package com.example.commander.adapter.batch.writer;

import com.example.commander.adapter.message.MqProperties;
import com.example.commander.domain.message.ReportMessageEnvelope;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;

/**
 * Always logs each chunk via {@link LoggingReportMessageWriter} (payload visibility), and
 * additionally sends it via {@link MqReportMessageWriter} (the real send) when {@code
 * commander.mq.enabled} is {@code true} — see {@link MqProperties}. Logging first either way,
 * so payload visibility never depends on the flag.
 */
@Component
public class CompositeReportMessageWriter implements ItemWriter<ReportMessageEnvelope> {

    private final LoggingReportMessageWriter loggingWriter;
    private final MqReportMessageWriter mqWriter;
    private final MqProperties mqProperties;

    public CompositeReportMessageWriter(
            LoggingReportMessageWriter loggingWriter, MqReportMessageWriter mqWriter, MqProperties mqProperties) {
        this.loggingWriter = loggingWriter;
        this.mqWriter = mqWriter;
        this.mqProperties = mqProperties;
    }

    @Override
    public void write(Chunk<? extends ReportMessageEnvelope> chunk) throws Exception {
        loggingWriter.write(chunk);
        if (mqProperties.isEnabled()) {
            mqWriter.write(chunk);
        }
    }
}
