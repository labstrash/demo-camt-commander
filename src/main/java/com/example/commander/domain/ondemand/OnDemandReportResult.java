package com.example.commander.domain.ondemand;

import com.example.commander.domain.audit.ReportCommandAuditStatus;
import java.util.List;

/**
 * The outcome of one {@code OnDemandReportService#trigger} call.
 *
 * <p>A rejection ({@code status} is one of the two {@code REJECTED_*} values) never reaches
 * message assembly, so {@link #messages()} is empty and {@link #detail()} explains why.
 *
 * <p>A request that does reach delivery can fan out to more than one message (an unbundled
 * config with multiple scopes/accounts). {@link #status()} aggregates {@link #messages()} for
 * that case: {@code FAILED} if any message failed, else {@code SENT} if any message sent,
 * else {@code SKIPPED_DUPLICATE} if every message was already-sent.
 *
 * @param status the overall outcome
 * @param detail human-readable reason for a rejection; {@code null} otherwise
 * @param messages per-message delivery outcomes; empty for a rejection
 */
public record OnDemandReportResult(
        ReportCommandAuditStatus status, String detail, List<OnDemandMessageOutcome> messages) {

    public OnDemandReportResult {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }
}
