package com.example.commander.domain.message;

/**
 * An account included in an outbound report message, stripped of internal DB identity.
 *
 * <p>Deliberately excludes {@code id} and {@code paymentTypeAssignmentId} —
 * {@link com.example.commander.domain.config.AccountAssignmentRow} carries those for
 * internal tree assembly, but they have no meaning to the downstream Executor.
 *
 * @param clearingNumber clearing/transit number for the account
 * @param accountNumber account identifier
 * @param accountBban Basic Bank Account Number (BBAN) format
 * @param currency ISO currency code for the account
 * @param balance the account's balance, or {@code null} when not supplied (every path except
 *     the EXT balance use case — scheduled/on-demand reports read live data downstream rather
 *     than carrying a balance in the message itself). Kept as the source system's own {@code
 *     String} format (signed decimal with comma, e.g. {@code "-579660,07"}) — no numeric
 *     parsing/rounding is done on this value anywhere in this pipeline.
 * @param settlementAmount the account's settlement amount, or {@code null} — same format/
 *     nullability rules as {@link #balance()}
 */
public record AccountAllocation(
        String clearingNumber,
        String accountNumber,
        String accountBban,
        String currency,
        String balance,
        String settlementAmount) {

    /**
     * Every existing caller — {@code AllocationMapper} and every scheduled/on-demand
     * assembly path — has no balance to supply; this keeps them unchanged.
     */
    public AccountAllocation(String clearingNumber, String accountNumber, String accountBban, String currency) {
        this(clearingNumber, accountNumber, accountBban, currency, null, null);
    }
}
