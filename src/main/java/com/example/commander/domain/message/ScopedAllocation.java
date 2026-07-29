package com.example.commander.domain.message;

import java.util.Objects;

/**
 * Pairs a {@link PaymentTypeAllocation} with the single scope it was grouped from.
 *
 * <p>Internal carrier between a {@link MessageGroupingStrategy} and {@code
 * ReportMessageAssembler} — never itself part of the wire payload. {@code scopeId} is
 * {@code null} for bundled groups (merged across scopes, no single owner) and non-null
 * for each unbundled group (one account/alias row from exactly one scope).
 *
 * @param scopeId the originating agreement scope, or {@code null} for bundled groups
 * @param allocation the grouped allocation
 */
public record ScopedAllocation(Long scopeId, PaymentTypeAllocation allocation) {
    public ScopedAllocation {
        Objects.requireNonNull(allocation, "allocation cannot be null");
    }
}
