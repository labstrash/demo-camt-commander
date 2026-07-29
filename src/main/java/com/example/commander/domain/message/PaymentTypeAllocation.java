package com.example.commander.domain.message;

import java.util.List;

/**
 * The accounts or aliases allocated to a single payment type within an outbound message.
 *
 * <p>Preserves the payment type association that would otherwise be lost when bundling
 * merges rows from multiple {@code PaymentTypeAssignmentNode}s into one message: bundled
 * messages carry one allocation per distinct payment type across all merged scopes, while
 * unbundled messages carry exactly one allocation with a single account or alias.
 *
 * <p>Mutually exclusive with accounts and aliases both populated, matching the invariant
 * on {@code PaymentTypeAssignmentNode}.
 *
 * @param paymentType the payment type code
 * @param accounts account assignments for this payment type (empty if alias allocation)
 * @param aliases alias assignments for this payment type (empty if account allocation)
 */
public record PaymentTypeAllocation(
        String paymentType, List<AccountAllocation> accounts, List<AliasAllocation> aliases) {
    public PaymentTypeAllocation {
        accounts = accounts == null ? List.of() : List.copyOf(accounts);
        aliases = aliases == null ? List.of() : List.copyOf(aliases);

        // Check the local (already-normalized) parameters directly, not via the
        // hasAccounts()/hasAliases() instance accessors — those read the canonical fields,
        // which a compact constructor hasn't assigned yet, and would NPE on every call.
        if (!accounts.isEmpty() && !aliases.isEmpty()) {
            throw new IllegalStateException(String.format(
                    "PaymentTypeGroup '%s' contains both accounts and aliases — "
                            + "this violates the account/alias mutual-exclusivity invariant",
                    paymentType));
        }
    }

    public boolean hasAccounts() {
        return !accounts.isEmpty();
    }

    public boolean hasAliases() {
        return !aliases.isEmpty();
    }

    public boolean hasBothAccountAndAlias() {
        return hasAccounts() && hasAliases();
    }

    public boolean isEmpty() {
        return accounts.isEmpty() && aliases.isEmpty();
    }

    public int totalAssignments() {
        return accounts.size() + aliases.size();
    }
}
