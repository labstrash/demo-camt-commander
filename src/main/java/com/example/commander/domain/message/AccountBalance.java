package com.example.commander.domain.message;

/**
 * A balance/settlement amount for one account, supplied externally (e.g. by the PHT balance
 * use case) rather than read live downstream like every other report path.
 *
 * <p>Kept as the source system's own {@code String} format (signed decimal with comma, e.g.
 * {@code "-579660,07"}) — no numeric parsing/rounding is done on either value anywhere in this
 * pipeline.
 *
 * @param balance the account's balance, as sent
 * @param settlementAmount the account's settlement amount, as sent
 */
public record AccountBalance(String balance, String settlementAmount) {}
