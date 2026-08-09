package com.example.camt.contract;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A single account allocated to a payment type.
 *
 * @param clearingNumber clearing/transit number for the account
 * @param accountNumber account identifier
 * @param accountBban Basic Bank Account Number (BBAN) format
 * @param currency ISO currency code for the account
 * @param balance the account's balance, or {@code null} on every path except the EXT balance
 *     use case. Kept as the source system's own {@code String} format (signed decimal with
 *     comma, e.g. {@code "-579660,07"}) — do not parse it as a number without accounting for
 *     that.
 * @param settlementAmount the account's settlement amount, or {@code null} — same
 *     format/nullability rules as {@link #balance()}
 */
public record AccountAllocation(
        @JsonProperty("clearingNumber") String clearingNumber,
        @JsonProperty("accountNumber") String accountNumber,
        @JsonProperty("accountBban") String accountBban,
        @JsonProperty("currency") String currency,
        @JsonProperty("balance") String balance,
        @JsonProperty("settlementAmount") String settlementAmount) {}
