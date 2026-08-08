package com.example.commander.domain.ext;

/**
 * One account's balance from an inbound EXT message.
 *
 * <p>{@code clearingNumber}/{@code accountNumber} are the raw substrings EXT sent — matched
 * against {@code CAMT.AccountAssignment.ClearingNumber}/{@code AccountNumber} by {@code
 * ExtReportMessageBuilder}, not parsed/transformed here. {@code balance}/{@code
 * settlementAmount} are kept as EXT's own string format (signed decimal with comma, e.g.
 * {@code "-579660,07"}) — no numeric parsing/rounding is done on either anywhere in this
 * pipeline.
 *
 * @param clearingNumber the account's clearing/transit number, as sent
 * @param accountNumber the account's identifier, as sent
 * @param balance the account's balance, as sent
 * @param settlementAmount the account's settlement amount, as sent
 */
public record ExtAccountBalance(String clearingNumber, String accountNumber, String balance, String settlementAmount) {}
