package com.example.commander.adapter.batch.writer;

import com.example.commander.adapter.message.MqProperties;
import com.example.commander.domain.audit.ReportCommandAuditStatus;
import com.example.commander.domain.message.ReportMessageEnvelope;
import com.example.commander.domain.message.ReportType;
import com.example.commander.service.ReportMessageDeliveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Thin {@code @StepScope} adapter: resolves this step's batch-specific context once
 * ({@code reportType}/{@code reportFrequency} from {@code JobParameters}, the target queue,
 * {@code StepExecution} identity), then calls {@link ReportMessageDeliveryService#deliver}
 * per chunk item — the actual dedup/send/dead-letter/audit logic lives there now, shared
 * with the on-demand trigger path (Phase 7, Decision 5), which has none of this batch
 * context to resolve.
 *
 * <p>{@code @StepScope}: resolves {@code reportType}/{@code reportFrequency} from {@code
 * JobParameters}, the target queue, and {@code StepExecution} once per job execution — a
 * firing only ever processes one report type, so there's nothing to re-resolve mid-step.
 */
@Component
@StepScope
public class MqReportMessageWriter implements ItemWriter<ReportMessageEnvelope> {

    private static final Logger log = LoggerFactory.getLogger(MqReportMessageWriter.class);

    private final ReportMessageDeliveryService deliveryService;
    private final String reportFrequency;
    private final String targetQueue;
    private final Long jobExecutionId;
    private final Long stepExecutionId;

    public MqReportMessageWriter(
            ReportMessageDeliveryService deliveryService,
            MqProperties mqProperties,
            @Value("#{jobParameters['reportType']}") String reportType,
            @Value("#{jobParameters['reportFrequency']}") String reportFrequency,
            @Value("#{stepExecution.jobExecutionId}") Long jobExecutionId,
            @Value("#{stepExecution.id}") Long stepExecutionId) {
        this.deliveryService = deliveryService;
        this.reportFrequency = reportFrequency;
        this.targetQueue = mqProperties.queueFor(ReportType.valueOf(reportType));
        this.jobExecutionId = jobExecutionId;
        this.stepExecutionId = stepExecutionId;
    }

    @Override
    public void write(Chunk<? extends ReportMessageEnvelope> chunk) throws Exception {
        int sent = 0;
        int skipped = 0;
        for (ReportMessageEnvelope item : chunk) {
            ReportCommandAuditStatus status =
                    deliveryService.deliver(item, targetQueue, reportFrequency, jobExecutionId, stepExecutionId);
            if (status == ReportCommandAuditStatus.SENT) {
                sent++;
            } else if (status == ReportCommandAuditStatus.SKIPPED_DUPLICATE) {
                skipped++;
            }
        }
        log.info(
                "Sent {}/{} message(s) to queue={} ({} skipped as duplicates)",
                sent,
                chunk.size(),
                targetQueue,
                skipped);
    }
}
