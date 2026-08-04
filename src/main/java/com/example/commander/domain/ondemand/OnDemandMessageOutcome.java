package com.example.commander.domain.ondemand;

import com.example.commander.domain.audit.ReportCommandAuditStatus;

/**
 * The delivery outcome for one assembled message within an on-demand trigger.
 *
 * <p>Most on-demand requests resolve to exactly one message (bundled or config-only
 * configs), but an unbundled config with multiple scopes/accounts fans out to several —
 * {@link OnDemandReportResult} carries one of these per message actually assembled and
 * handed to {@code ReportMessageDeliveryService}.
 *
 * @param messageId the delivered message's identifier
 * @param correlationId the delivered message's correlation identifier
 * @param status this message's individual audit status
 */
public record OnDemandMessageOutcome(String messageId, String correlationId, ReportCommandAuditStatus status) {}
