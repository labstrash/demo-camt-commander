package com.example.commander.service;

import com.example.commander.adapter.message.MqProperties;
import com.example.commander.domain.audit.ReportCommandAuditEntry;
import com.example.commander.domain.audit.ReportCommandAuditStatus;
import com.example.commander.domain.config.RecipientRow;
import com.example.commander.domain.config.ReportConfigRow;
import com.example.commander.domain.config.ReportConfigTree;
import com.example.commander.domain.message.AssemblyContext;
import com.example.commander.domain.message.Recipient;
import com.example.commander.domain.message.RecipientType;
import com.example.commander.domain.message.ReportContext;
import com.example.commander.domain.message.ReportMessageEnvelope;
import com.example.commander.domain.message.ReportMessageIdGenerator;
import com.example.commander.domain.message.ReportType;
import com.example.commander.domain.message.TriggerType;
import com.example.commander.domain.ondemand.OnDemandMessageOutcome;
import com.example.commander.domain.ondemand.OnDemandReportRequest;
import com.example.commander.domain.ondemand.OnDemandReportResult;
import com.example.commander.domain.report.ReportWindow;
import com.example.commander.repository.ReportCommandAuditRepository;
import com.example.commander.repository.ReportConfigRepository;
import com.example.commander.repository.ReportConfigTreeRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates an on-demand report trigger: resolve recipient/config → validate the
 * caller-supplied window → assemble → deliver — reusing {@link ReportMessageAssembler} and
 * {@link ReportMessageDeliveryService} rather than a parallel implementation.
 *
 * <p><b>Rejections</b> (recipient/config didn't resolve, or the caller-supplied window isn't
 * valid yet) never reach assembly or delivery — they still get one {@code
 * CAMT.ReportCommandAudit} row each, using the {@code REJECTED_*} status values and the
 * nullable-column shape {@link ReportCommandAuditEntry} already supports for rows with no
 * resolved config/window.
 *
 * <p>Called from {@code OnDemandMessageListener} — fire-and-forget, no reply-to. The result is
 * logged by the caller; the audit row is the durable record.
 */
@Service
public class OnDemandReportService {

    private static final Logger log = LoggerFactory.getLogger(OnDemandReportService.class);

    private static final String REJECTION_LOG_MESSAGE = "On-demand request rejected: {}";

    /**
     * {@code report_version} is {@code NVARCHAR(3) NOT NULL} — for a {@code
     * REJECTED_CONFIG_NOT_ELIGIBLE} row, no config (and so no real version) was ever
     * resolved. This 3-character sentinel fills that gap.
     */
    private static final String UNRESOLVED_REPORT_VERSION = "N/A";

    private final ReportConfigRepository reportConfigRepository;
    private final ReportConfigTreeRepository reportConfigTreeRepository;
    private final ReportMessageAssembler reportMessageAssembler;
    private final ReportMessageDeliveryService deliveryService;
    private final ReportCommandAuditRepository auditRepository;
    private final MqProperties mqProperties;
    private final CorrelationIdGenerator correlationIdGenerator;
    private final ReportMessageIdGenerator messageIdGenerator;
    private final Clock clock;

    public OnDemandReportService(
            ReportConfigRepository reportConfigRepository,
            ReportConfigTreeRepository reportConfigTreeRepository,
            ReportMessageAssembler reportMessageAssembler,
            ReportMessageDeliveryService deliveryService,
            ReportCommandAuditRepository auditRepository,
            MqProperties mqProperties,
            CorrelationIdGenerator correlationIdGenerator,
            ReportMessageIdGenerator messageIdGenerator,
            Clock clock) {
        this.reportConfigRepository = reportConfigRepository;
        this.reportConfigTreeRepository = reportConfigTreeRepository;
        this.reportMessageAssembler = reportMessageAssembler;
        this.deliveryService = deliveryService;
        this.auditRepository = auditRepository;
        this.mqProperties = mqProperties;
        this.correlationIdGenerator = correlationIdGenerator;
        this.messageIdGenerator = messageIdGenerator;
        this.clock = clock;
    }

    /**
     * Triggers an on-demand report for {@code request}.
     *
     * @param request the caller's request
     * @return the outcome — a rejection, or the aggregated delivery result
     */
    public OnDemandReportResult trigger(OnDemandReportRequest request) {
        Optional<RecipientRow> recipient =
                reportConfigRepository.findRecipientByTypeAndValue(request.recipientType(), request.recipientValue());
        if (recipient.isEmpty()) {
            String detail = "No recipient found for type=%s, value=%s"
                    .formatted(request.recipientType(), request.recipientValue());
            log.info(REJECTION_LOG_MESSAGE, detail);
            return rejectConfigNotEligible(request, detail);
        }

        Optional<ReportConfigRow> config = reportConfigRepository.findActiveByRecipientAndReportType(
                recipient.get().id(), request.reportType());
        if (config.isEmpty()) {
            String detail = "No active ReportConfig for recipientId=%s, reportType=%s"
                    .formatted(recipient.get().id(), request.reportType());
            log.info(REJECTION_LOG_MESSAGE, detail);
            return rejectConfigNotEligible(request, detail);
        }

        return triggerForConfig(request, recipient.get(), config.get());
    }

    private OnDemandReportResult triggerForConfig(
            OnDemandReportRequest request, RecipientRow recipient, ReportConfigRow config) {
        ReportWindow window;
        try {
            window = new ReportWindow(request.windowStartUtc(), request.windowEndUtc());
        } catch (IllegalArgumentException ex) {
            log.info(REJECTION_LOG_MESSAGE, ex.getMessage());
            return rejectInvalidWindow(request, config, null, ex.getMessage());
        }

        if (window.windowEndUtc().isAfter(clock.instant())) {
            String detail = "Requested window [%s, %s) ends after now — source data can't be final yet"
                    .formatted(window.windowStartUtc(), window.windowEndUtc());
            log.info(REJECTION_LOG_MESSAGE, detail);
            return rejectInvalidWindow(request, config, window, detail);
        }

        Recipient recipientRef = new Recipient(
                recipient.id(), RecipientType.valueOf(recipient.type()), recipient.value(), recipient.name());
        AssemblyContext context = new AssemblyContext(
                new ReportContext(window, config.reportVersion(), TriggerType.ON_DEMAND),
                recipientRef,
                request.requestorName());

        ReportConfigTree tree =
                reportConfigTreeRepository.assembleTrees(List.of(config)).getFirst();
        List<ReportMessageEnvelope> messages = reportMessageAssembler.assemble(tree, context);

        String targetQueue = mqProperties.queueFor(config.reportType());
        List<OnDemandMessageOutcome> outcomes = new ArrayList<>(messages.size());
        for (ReportMessageEnvelope message : messages) {
            log.debug("Report Message={}", message.payload());
            ReportCommandAuditStatus status = deliveryService.deliver(message, targetQueue, null, null, null);
            outcomes.add(new OnDemandMessageOutcome(
                    message.payload().messageId(), message.payload().correlationId(), status));
        }

        return new OnDemandReportResult(aggregateStatus(outcomes), null, outcomes);
    }

    /**
     * {@code FAILED} if any message failed, else {@code SENT} — including the vacuous case
     * where {@code outcomes} is empty (every resolved scope's assignments were dangling, so
     * assembly produced nothing to attempt sending): no failures occurred, so there's nothing
     * to report as wrong.
     */
    private static ReportCommandAuditStatus aggregateStatus(List<OnDemandMessageOutcome> outcomes) {
        boolean anyFailed = outcomes.stream().anyMatch(o -> o.status() == ReportCommandAuditStatus.FAILED);
        return anyFailed ? ReportCommandAuditStatus.FAILED : ReportCommandAuditStatus.SENT;
    }

    private OnDemandReportResult rejectConfigNotEligible(OnDemandReportRequest request, String detail) {
        insertRejectionAudit(request, null, null, ReportCommandAuditStatus.REJECTED_CONFIG_NOT_ELIGIBLE, detail);
        return new OnDemandReportResult(ReportCommandAuditStatus.REJECTED_CONFIG_NOT_ELIGIBLE, detail, List.of());
    }

    private OnDemandReportResult rejectInvalidWindow(
            OnDemandReportRequest request, ReportConfigRow config, ReportWindow window, String detail) {
        insertRejectionAudit(request, config, window, ReportCommandAuditStatus.REJECTED_INVALID_WINDOW, detail);
        return new OnDemandReportResult(ReportCommandAuditStatus.REJECTED_INVALID_WINDOW, detail, List.of());
    }

    /** Builds and inserts a rejection-audit row. */
    private void insertRejectionAudit(
            OnDemandReportRequest request,
            ReportConfigRow config,
            ReportWindow window,
            ReportCommandAuditStatus status,
            String detail) {
        Instant now = clock.instant();
        ReportCommandAuditEntry entry = ReportCommandAuditEntry.builder()
                .messageId(rejectionMessageId(config, request.reportType()))
                .correlationId(rejectionCorrelationId(config, window))
                .reportConfigId(config != null ? config.id() : null)
                .configId(config != null ? String.valueOf(config.configId()) : null)
                .agreementScopeId(null)
                .reportType(request.reportType())
                .reportVersion(config != null ? config.reportVersion() : UNRESOLVED_REPORT_VERSION)
                .reportFrequency(null)
                .triggerType(TriggerType.ON_DEMAND)
                .windowStartUtc(window != null ? window.windowStartUtc() : now)
                .windowEndUtc(window != null ? window.windowEndUtc() : now)
                .isBundled(config != null && config.isBundled())
                .accountCount(0)
                .paymentTypeCount(0)
                .recipientType(request.recipientType())
                .recipientValue(request.recipientValue())
                .mqQueueName(null)
                .mqMessageId(null)
                .status(status)
                .errorMessage(detail)
                .sentAt(now)
                .retryCount(0)
                .jobExecutionId(null)
                .stepExecutionId(null)
                .requestorName(request.requestorName())
                .build();

        auditRepository.insert(entry);
    }

    /**
     * {@code message_id} is {@code NOT NULL} even for a rejection that never assembled a real
     * message — reuses {@link ReportMessageIdGenerator} with the resolved {@code configId}
     * where available, or {@code 0} when no config resolved at all ({@code
     * REJECTED_CONFIG_NOT_ELIGIBLE}).
     */
    private String rejectionMessageId(ReportConfigRow config, ReportType requestedReportType) {
        long reportId = config != null ? config.configId() : 0;
        return messageIdGenerator.generateMessageId(reportId, requestedReportType);
    }

    /**
     * {@code correlation_id} is {@code NOT NULL} even for a rejection. For {@code
     * REJECTED_INVALID_WINDOW}, a config and a computed (if invalid) window are both known —
     * derives the same way {@link CorrelationIdGenerator} derives a bundled/config-only
     * message's correlationId, so a retried identical request lands on the same correlation
     * lineage. {@code REJECTED_CONFIG_NOT_ELIGIBLE} has neither — nothing to derive from, so a
     * fresh random identifier is the only option.
     */
    private String rejectionCorrelationId(ReportConfigRow config, ReportWindow window) {
        if (config == null || window == null) {
            return UUID.randomUUID().toString();
        }
        return correlationIdGenerator.generate(
                config.id(), null, window.windowStartUtc(), window.windowEndUtc(), List.of());
    }
}
