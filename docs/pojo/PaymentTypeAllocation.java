package com.example.camt.contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * The accounts or aliases allocated to a single payment type within the message.
 *
 * <p>{@code accounts} and {@code aliases} are mutually exclusive — exactly one of the two is
 * ever non-empty for a given allocation, never both. Both can be empty only in the degenerate
 * case of a config with no scopes at all (a "config-only" message), in which case {@code
 * paymentTypes} on the envelope is an empty list and this type never appears.
 *
 * @param paymentType the payment type code
 * @param accounts account assignments for this payment type (empty if this is an alias
 *     allocation)
 * @param aliases alias assignments for this payment type (empty if this is an account
 *     allocation)
 */
public record PaymentTypeAllocation(
        @JsonProperty("paymentType") String paymentType,
        @JsonProperty("accounts") List<AccountAllocation> accounts,
        @JsonProperty("aliases") List<AliasAllocation> aliases) {}
