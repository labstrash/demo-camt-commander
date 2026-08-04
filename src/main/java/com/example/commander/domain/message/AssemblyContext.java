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
 *     except the PHT balance use case. {@link com.example.commander.service.AllocationMapper}
 *     populates {@link AccountAllocation#balance()}/{@link AccountAllocation#settlementAmount()}
 *     from this map when non-empty, and — for that same case — omits any account this map has
 *     no entry for, since a PHT push only ever covers the accounts it actually carries data for.
 */
public record AssemblyContext(
        ReportContext reportContext,
        Recipient recipient,
        String requestorName,
        Map<AccountKey, AccountBalance> accountBalances) {
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
        this(reportContext, recipient, requestorName, Map.of());
    }
}
