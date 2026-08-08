package com.example.commander.domain.assembly;

import com.example.commander.domain.message.PaymentTypeAllocation;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Generates deterministic correlation IDs for report messages.
 *
 * <p>Correlation IDs are reproducible from message content, allowing
 * idempotent processing and deduplication.
 */
@Component
public class CorrelationIdGenerator {

    /**
     * Generates a correlation ID for a report message.
     *
     * @param configId the report configuration ID
     * @param scopeId the scope ID (null for bundled/config-only)
     * @param windowStartUtc start of the reporting window
     * @param windowEndUtc end of the reporting window
     * @param paymentTypeAllocations the payment type groups in this message
     * @return a deterministic UUID correlation ID
     */
    public String generate(
            long configId,
            Long scopeId,
            Instant windowStartUtc,
            Instant windowEndUtc,
            List<PaymentTypeAllocation> paymentTypeAllocations) {

        String basis = buildBasis(configId, scopeId, windowStartUtc, windowEndUtc, paymentTypeAllocations);
        return UUID.nameUUIDFromBytes(basis.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private String buildBasis(
            long configId,
            Long scopeId,
            Instant windowStartUtc,
            Instant windowEndUtc,
            List<PaymentTypeAllocation> paymentTypeAllocations) {

        if (scopeId == null) {
            // Bundled or config-only message
            return String.format("%d|%s|%s", configId, windowStartUtc, windowEndUtc);
        } else {
            // Unbundled message - include allocation key for uniqueness
            String allocationKey = extractAllocationKey(paymentTypeAllocations);
            return String.format("%d|%d|%s|%s|%s", configId, scopeId, windowStartUtc, windowEndUtc, allocationKey);
        }
    }

    private String extractAllocationKey(List<PaymentTypeAllocation> paymentTypeAllocations) {
        if (paymentTypeAllocations == null || paymentTypeAllocations.isEmpty()) {
            throw new IllegalArgumentException("Payment type groups cannot be empty for unbundled messages");
        }

        PaymentTypeAllocation group = paymentTypeAllocations.get(0);

        if (group.hasAccounts()) {
            return group.accounts().getFirst().accountNumber();
        } else if (group.hasAliases()) {
            return group.aliases().getFirst().aliasValue();
        } else {
            throw new IllegalStateException("Payment type group has no accounts or aliases");
        }
    }
}
