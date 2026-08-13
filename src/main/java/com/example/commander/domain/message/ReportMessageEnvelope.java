package com.example.commander.domain.message;

import java.util.Objects;

/**
 * The report pipeline's chunk item type — wraps the wire-facing {@link ReportMessage}
 * with internal-only identity fields the pipeline itself needs but the Executor never sees.
 *
 * <p>Only {@link #payload()} is ever serialized to MQ or logged by
 * {@code LoggingReportMessageWriter}. {@code configId}/{@code scopeId} exist
 * solely so a later stage can populate {@code CAMT.DeadLetterMessage} without re-deriving
 * them — they are deliberately not part of {@code ReportMessage} itself, which only
 * carries what the Executor actually needs.
 *
 * @param payload the wire-facing outbound message
 * @param configId surrogate ID of the originating {@code ReportConfig} row
 * @param scopeId the single originating scope ID for unbundled messages, or
 *     {@code null} for bundled/config-only messages, which have no single scope
 * @param recipientId the recipient's internal DB row id, present only between the reader
 *     stashing it on the placeholder message's {@link AssemblyContext} and {@code
 *     RecipientResolvingReportMessageProcessor} resolving it — {@code null} once resolved,
 *     and for every non-scheduled path. Deliberately not part of {@link Recipient}/{@link
 *     ReportMessage} itself, since {@link #payload()} is what gets serialized to MQ.
 */
public record ReportMessageEnvelope(ReportMessage payload, long configId, Long scopeId, Long recipientId) {
    public ReportMessageEnvelope {
        Objects.requireNonNull(payload, "payload cannot be null");
        if (configId <= 0) {
            throw new IllegalArgumentException("configId must be positive");
        }

        // Validate that bundled state matches scopeId
        boolean hasSingleScope = payload.bundled() == (scopeId == null);
        if (!hasSingleScope) {
            throw new IllegalStateException(String.format(
                    "Bundled messages must have null scopeId, unbundled must have a scopeId. "
                            + "bundled=%s, scopeId=%s",
                    payload.bundled(), scopeId));
        }
    }

    /**
     * Every writer/audit/dead-letter call site downstream of resolution has no unresolved
     * recipientId to carry — this keeps them unchanged.
     */
    public ReportMessageEnvelope(ReportMessage payload, long configId, Long scopeId) {
        this(payload, configId, scopeId, null);
    }

    public boolean isBundled() {
        return payload.bundled();
    }

    public boolean isSingleScope() {
        return scopeId != null;
    }

    public boolean isConfigOnly() {
        return payload.hasNoPaymentTypes();
    }
}
