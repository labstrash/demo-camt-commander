package com.example.commander.domain.message;

import java.util.Objects;

/**
 * Complete context for assembling outbound report messages.
 *
 * <p>Contains all information needed to assemble messages, including report metadata,
 * recipient information, and audit details.
 *
 * <p>This context differs between scheduled and on-demand execution paths:
 * <ul>
 *   <li><b>Window boundaries:</b> Calculated by the scheduler for scheduled runs;
 *       provided directly by the request for on-demand</li>
 *   <li><b>Report version:</b> Uses the config's version for scheduled;
 *       request-supplied for on-demand</li>
 *   <li><b>Requestor name:</b> Null for scheduled; audit/traceability value for on-demand</li>
 * </ul>
 *
 * @param reportContext the report metadata context
 * @param recipient the message recipient
 * @param requestorName who initiated the request (null for scheduled, auditable value for on-demand)
 */
public record AssemblyContext(ReportContext reportContext, Recipient recipient, String requestorName) {
    public AssemblyContext {
        Objects.requireNonNull(reportContext, "reportContext cannot be null");
        Objects.requireNonNull(recipient, "recipient cannot be null");
    }
}
