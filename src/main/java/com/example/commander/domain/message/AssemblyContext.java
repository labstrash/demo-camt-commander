package com.example.commander.domain.message;

import java.util.Map;
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
 * @param accountBalances externally supplied balances, keyed by account — empty for every path
 *     except the EXT balance use case. {@link com.example.commander.domain.assembly.AllocationMapper}
 *     populates {@link AccountAllocation#balance()}/{@link AccountAllocation#settlementAmount()}
 *     from this map when non-empty, and — for that same case — omits any account this map has
 *     no entry for, since a EXT push only ever covers the accounts it actually carries data for.
 * @param recipientId the recipient's internal DB row id, carried through only for the scheduled
 *     pipeline's own use (see {@code ReportPipelineItemReader}'s placeholder {@link Recipient} and
 *     {@code RecipientResolvingReportMessageProcessor}, which resolves it) — {@code null} for
 *     on-demand and EXT paths, which already resolve the recipient before building this context.
 *     Never part of {@link Recipient} itself, since that type ends up inside {@link ReportMessage},
 *     which is serialized onto MQ; this field lives here (and on {@link ReportMessageEnvelope}),
 *     neither of which is ever serialized.
 */
public record AssemblyContext(
        ReportContext reportContext,
        Recipient recipient,
        String requestorName,
        Map<AccountKey, AccountBalance> accountBalances,
        Long recipientId) {
    public AssemblyContext {
        Objects.requireNonNull(reportContext, "reportContext cannot be null");
        Objects.requireNonNull(recipient, "recipient cannot be null");
        accountBalances = accountBalances == null ? Map.of() : Map.copyOf(accountBalances);
    }

    /**
     * Every existing caller — scheduled and on-demand assembly — has no external balance data
     * to supply; this keeps them unchanged.
     */
    public AssemblyContext(ReportContext reportContext, Recipient recipient, String requestorName) {
        this(reportContext, recipient, requestorName, Map.of(), null);
    }

    /**
     * EXT balance-push path: has balances but, like every non-scheduled caller, no unresolved
     * recipientId to carry forward — the recipient is already fully resolved by this point.
     */
    public AssemblyContext(
            ReportContext reportContext,
            Recipient recipient,
            String requestorName,
            Map<AccountKey, AccountBalance> accountBalances) {
        this(reportContext, recipient, requestorName, accountBalances, null);
    }
}
