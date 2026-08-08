package com.example.commander.domain.message;

/**
 * Identifies an account for balance lookup during message assembly — the same
 * {@code (clearingNumber, accountNumber)} pair {@link com.example.commander.domain.config.AccountAssignmentRow}
 * carries, used to match a push of external balance data (e.g. the EXT balance use case) back
 * onto the config's own account rows.
 *
 * @param clearingNumber clearing/transit number for the account
 * @param accountNumber account identifier
 */
public record AccountKey(String clearingNumber, String accountNumber) {}
