package com.example.commander.service;

import com.example.commander.adapter.message.MqResilienceProperties;
import com.example.commander.adapter.message.ResilientMqSender;
import com.example.commander.adapter.message.SendOutcome;
import com.example.commander.domain.audit.ReportCommandAuditEntry;
import com.example.commander.domain.audit.ReportCommandAuditStatus;
import com.example.commander.domain.deadletter.DeadLetterMessage;
import com.example.commander.domain.message.ReportMessage;
import com.example.commander.domain.message.ReportMessageEnvelope;
import com.example.commander.repository.DeadLetterMessageRepository;
import com.example.commander.repository.ReportCommandAuditRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * The one place a resolved, ready-to-send {@link ReportMessageEnvelope} actually goes to
 * MQ: serialize → {@link ResilientMqSender#send} → dead-letter-on-failure → one {@code
 * CAMT.ReportCommandAudit} row per attempt. Extracted out of {@code MqReportMessageWriter}
 * (Phase 6) so the on-demand path (Phase 7) can reuse the exact same logic instead of a
 * second implementation that could silently drift from this one — {@code
 * MqReportMessageWriter} is now a thin {@code @StepScope} adapter that resolves its
 * batch-specific context once, then calls {@link #deliver} per chunk item.
 *
 * <p>Plain {@code @Component}, no {@code @StepScope} — nothing here depends on an active
 * Spring Batch step context, which is exactly what makes it callable from an HTTP request
 * handler that has no such context at all.
 *
 * <p>A message {@link ResilientMqSender} couldn't deliver (retries exhausted, permanent
 * failure, or the circuit breaker open) is written to {@code CAMT.DeadLetterMessage} instead
 * of failing the caller — the whole point of the dead-letter tier is that one message's
 * delivery trouble doesn't cascade into a failed batch job (or a failed on-demand request
 * that could otherwise have at least recorded the failure).
 *
 * <p>Payload serialization happens here, before the resilient send — not inside {@link
 * ResilientMqSender}, which only ever handles an already-serialized string (the recovery job
 * resends a stored one, with nothing to serialize).
 */
@Component
public class ReportMessageDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(ReportMessageDeliveryService.class);

    private final ObjectMapper objectMapper;
    private final ResilientMqSender sender;
    private final DeadLetterMessageRepository deadLetterRepository;
    private final ReportCommandAuditRepository auditRepository;
    private final Clock clock;
    private final int deadLetterMaxRetries;

    public ReportMessageDeliveryService(
            ObjectMapper objectMapper,
            ResilientMqSender sender,
            DeadLetterMessageRepository deadLetterRepository,
            ReportCommandAuditRepository auditRepository,
            Clock clock,
            MqResilienceProperties mqResilienceProperties) {
        this.objectMapper = objectMapper;
        this.sender = sender;
        this.deadLetterRepository = deadLetterRepository;
        this.auditRepository = auditRepository;
        this.clock = clock;
        this.deadLetterMaxRetries = mqResilienceProperties.getDeadLetterMaxRetries();
    }

    /**
     * Delivers one message: send, dead-letter-on-failure, audit row.
     *
     * @param item the resolved pipeline message to deliver
     * @param targetQueue the MQ queue to send to
     * @param reportFrequency {@code jobParameters['reportFrequency']} for the primary writer,
     *     or {@code null} when there's no such context (on-demand)
     * @param jobExecutionId the owning {@code StepExecution}'s job execution ID, or {@code
     *     null} when there's no active Spring Batch step (on-demand)
     * @param stepExecutionId the owning {@code StepExecution}'s ID, or {@code null} when
     *     there's no active Spring Batch step (on-demand)
     * @return the resulting audit status for this attempt
     */
    public ReportCommandAuditStatus deliver(
            ReportMessageEnvelope item,
            String targetQueue,
            String reportFrequency,
            Long jobExecutionId,
            Long stepExecutionId) {
        ReportMessage payload = item.payload();
        DeliveryContext deliveryContext =
                new DeliveryContext(targetQueue, reportFrequency, jobExecutionId, stepExecutionId);

        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JacksonException ex) {
            // Unlike a send failure, there's no valid, resendable payload to dead-letter here
            // — the object itself can't be turned into JSON, so retrying would fail exactly
            // the same way every time. Record it as a permanent failure via the audit trail
            // only, rather than crashing the caller (the batch step, or an on-demand HTTP
            // thread once Phase 4 lands).
            log.error(
                    "Failed to serialize messageId={} correlationId={} — recording as FAILED, no dead-letter row",
                    payload.messageId(),
                    payload.correlationId(),
                    ex);
            insertAudit(auditEntry(
                    item,
                    ReportCommandAuditStatus.FAILED,
                    null,
                    "Serialization failed: " + ex.getMessage(),
                    0,
                    deliveryContext));
            return ReportCommandAuditStatus.FAILED;
        }
        SendOutcome outcome = sender.send(targetQueue, json);

        if (outcome.isFailure()) {
            deadLetter(item, json, outcome, targetQueue);
            insertAudit(
                    auditEntry(item, ReportCommandAuditStatus.FAILED, null, errorMessage(outcome), 0, deliveryContext));
            return ReportCommandAuditStatus.FAILED;
        }

        insertAudit(auditEntry(item, ReportCommandAuditStatus.SENT, outcome.jmsMessageId(), null, 0, deliveryContext));
        log.info(
                "Delivered messageId={} correlationId={} to queue={} (jmsMessageId={})",
                payload.messageId(),
                payload.correlationId(),
                targetQueue,
                outcome.jmsMessageId());
        return ReportCommandAuditStatus.SENT;
    }

    private void deadLetter(ReportMessageEnvelope item, String json, SendOutcome outcome, String targetQueue) {
        log.warn(
                "Dead-lettering messageId={} (outcome={}) for queue={}",
                item.payload().messageId(),
                outcome.type(),
                targetQueue,
                outcome.cause());
        deadLetterRepository.insert(new DeadLetterMessage(
                item.payload().messageId(),
                item.configId(),
                item.scopeId(),
                item.payload().type(),
                json,
                targetQueue,
                deadLetterMaxRetries,
                clock.instant()));
    }

    /**
     * Builds this attempt's audit row. {@code retryCount} is always 0 here — {@link
     * ResilientMqSender}'s in-process retries are opaque to this caller, so one call to
     * {@code sender.send()} is one attempt from here, regardless of how many JMS-level
     * retries happened inside it. {@code DeadLetterRecoveryJob} rows are the ones where
     * {@code retryCount} means something (the recovery attempt number).
     */
    private ReportCommandAuditEntry auditEntry(
            ReportMessageEnvelope item,
            ReportCommandAuditStatus status,
            String mqMessageId,
            String errorMessage,
            int retryCount,
            DeliveryContext deliveryContext) {
        ReportMessage payload = item.payload();
        return ReportCommandAuditEntry.builder()
                .messageId(payload.messageId())
                .correlationId(payload.correlationId())
                .reportConfigId(item.configId())
                .configId(String.valueOf(payload.reportId()))
                .agreementScopeId(item.scopeId())
                .reportType(payload.type())
                .reportVersion(payload.version())
                .reportFrequency(deliveryContext.reportFrequency())
                .triggerType(payload.triggerType())
                .windowStartUtc(payload.startDateTimeUtc())
                .windowEndUtc(payload.endDateTimeUtc())
                .isBundled(payload.bundled())
                .accountCount(payload.totalAccountAssignments())
                .paymentTypeCount(payload.paymentTypeCount())
                .recipientType(payload.recipient().type().name())
                .recipientValue(payload.recipient().value())
                .mqQueueName(deliveryContext.targetQueue())
                .mqMessageId(mqMessageId)
                .status(status)
                .errorMessage(errorMessage)
                .sentAt(clock.instant())
                .retryCount(retryCount)
                .jobExecutionId(deliveryContext.jobExecutionId())
                .stepExecutionId(deliveryContext.stepExecutionId())
                .requestorName(payload.requestorName())
                .build();
    }

    private void insertAudit(ReportCommandAuditEntry entry) {
        auditRepository.insert(entry);
    }

    /**
     * The per-{@link #deliver} invocation environment fields threaded through every {@link
     * #auditEntry} call for that delivery — grouped so the method doesn't need one parameter
     * per field.
     */
    private record DeliveryContext(
            String targetQueue, String reportFrequency, Long jobExecutionId, Long stepExecutionId) {}

    private static String errorMessage(SendOutcome outcome) {
        String cause = outcome.cause() != null ? outcome.cause().getMessage() : null;
        return cause != null ? outcome.type() + ": " + cause : outcome.type().name();
    }
}
