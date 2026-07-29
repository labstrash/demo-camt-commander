package com.example.commander.domain.message;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * An outbound message for report generation, produced by the fan-out and bundling process.
 *
 * <p>Each instance represents a single message to be published to the message queue,
 * containing all information needed to generate a report: configuration details,
 * window boundaries, recipient information, and account or alias data grouped by
 * payment type.
 *
 * <p>Messages can be:
 * <ul>
 *   <li><b>Config-only:</b> No payment type allocations (zero-scope case)</li>
 *   <li><b>Bundled:</b> One allocation per distinct payment type, merged across scopes</li>
 *   <li><b>Unbundled:</b> Single allocation with one account or alias from a specific scope</li>
 * </ul>
 */
public record ReportMessage(
        int configId,
        String reportType,
        String reportVersion,
        Instant windowStartUtc,
        Instant windowEndUtc,
        boolean bundled,
        TriggerType triggerType,
        Recipient recipient,
        List<PaymentTypeAllocation> paymentTypeAllocations,
        String requestorName,
        String correlationId,
        String messageId) {
    public ReportMessage {
        validateConfigId(configId);
        validateReportType(reportType);
        Objects.requireNonNull(reportVersion, "reportVersion cannot be null");
        Objects.requireNonNull(windowStartUtc, "windowStartUtc cannot be null");
        Objects.requireNonNull(windowEndUtc, "windowEndUtc cannot be null");
        Objects.requireNonNull(triggerType, "triggerType cannot be null");
        Objects.requireNonNull(recipient, "recipient cannot be null");
        Objects.requireNonNull(correlationId, "correlationId cannot be null");
        Objects.requireNonNull(messageId, "messageId cannot be null");

        paymentTypeAllocations = paymentTypeAllocations == null ? List.of() : List.copyOf(paymentTypeAllocations);

        if (windowStartUtc.isAfter(windowEndUtc)) {
            throw new IllegalArgumentException("windowStartUtc must be before windowEndUtc");
        }
    }

    private static void validateConfigId(int configId) {
        if (configId <= 0) {
            throw new IllegalArgumentException("configId must be positive");
        }
    }

    private static void validateReportType(String reportType) {
        if (reportType == null || reportType.isBlank()) {
            throw new IllegalArgumentException("reportType cannot be null or blank");
        }
    }

    /**
     * Returns the number of distinct payment types included in this message.
     *
     * @return payment type count
     */
    public int paymentTypeCount() {
        return paymentTypeAllocations.size();
    }

    /**
     * Returns the number of account assignments in this message, across all payment types.
     *
     * @return account count
     */
    public int totalAccountAssignments() {
        return paymentTypeAllocations.stream()
                .mapToInt(allocation -> allocation.accounts().size())
                .sum();
    }

    /**
     * Returns true if this message contains no payment type allocations.
     *
     * <p>Config-only messages occur for report configurations with zero scopes.
     *
     * @return true if there are no payment type allocations
     */
    public boolean hasNoPaymentTypes() {
        return paymentTypeAllocations.isEmpty();
    }

    /**
     * Returns true if this message contains any payment type allocations.
     *
     * @return true if there are payment type allocations
     */
    public boolean hasPaymentTypes() {
        return !paymentTypeAllocations.isEmpty();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Integer configId;
        private String reportType;
        private String reportVersion;
        private Instant windowStartUtc;
        private Instant windowEndUtc;
        private Boolean bundled;
        private TriggerType triggerType;
        private Recipient recipient;
        private List<PaymentTypeAllocation> paymentTypeAllocations;
        private String requestorName;
        private String correlationId;
        private String messageId;

        private Builder() {}

        public Builder configId(int configId) {
            this.configId = configId;
            return this;
        }

        public Builder reportType(String reportType) {
            this.reportType = reportType;
            return this;
        }

        public Builder reportVersion(String reportVersion) {
            this.reportVersion = reportVersion;
            return this;
        }

        public Builder windowStartUtc(Instant windowStartUtc) {
            this.windowStartUtc = windowStartUtc;
            return this;
        }

        public Builder windowEndUtc(Instant windowEndUtc) {
            this.windowEndUtc = windowEndUtc;
            return this;
        }

        public Builder bundled(boolean bundled) {
            this.bundled = bundled;
            return this;
        }

        public Builder triggerType(TriggerType triggerType) {
            this.triggerType = triggerType;
            return this;
        }

        public Builder recipient(Recipient recipient) {
            this.recipient = recipient;
            return this;
        }

        public Builder paymentTypeGroups(List<PaymentTypeAllocation> paymentTypeAllocations) {
            this.paymentTypeAllocations = paymentTypeAllocations;
            return this;
        }

        public Builder requestorName(String requestorName) {
            this.requestorName = requestorName;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder messageId(String messageId) {
            this.messageId = messageId;
            return this;
        }

        public ReportMessage build() {
            if (configId == null) {
                throw new IllegalStateException("configId is required");
            }
            if (reportType == null) {
                throw new IllegalStateException("reportType is required");
            }
            if (reportVersion == null) {
                throw new IllegalStateException("reportVersion is required");
            }
            if (windowStartUtc == null) {
                throw new IllegalStateException("windowStartUtc is required");
            }
            if (windowEndUtc == null) {
                throw new IllegalStateException("windowEndUtc is required");
            }
            if (bundled == null) {
                throw new IllegalStateException("bundled is required");
            }
            if (triggerType == null) {
                throw new IllegalStateException("triggerType is required");
            }
            if (recipient == null) {
                throw new IllegalStateException("recipient is required");
            }
            if (correlationId == null) {
                throw new IllegalStateException("correlationId is required");
            }
            if (messageId == null) {
                throw new IllegalStateException("messageId is required");
            }

            return new ReportMessage(
                    configId,
                    reportType,
                    reportVersion,
                    windowStartUtc,
                    windowEndUtc,
                    bundled,
                    triggerType,
                    recipient,
                    paymentTypeAllocations,
                    requestorName,
                    correlationId,
                    messageId);
        }

        public Builder from(ReportMessage existing) {
            this.configId = existing.configId();
            this.reportType = existing.reportType();
            this.reportVersion = existing.reportVersion();
            this.windowStartUtc = existing.windowStartUtc();
            this.windowEndUtc = existing.windowEndUtc();
            this.bundled = existing.bundled();
            this.triggerType = existing.triggerType();
            this.recipient = existing.recipient();
            this.paymentTypeAllocations = existing.paymentTypeAllocations();
            this.requestorName = existing.requestorName();
            this.correlationId = existing.correlationId();
            this.messageId = existing.messageId();
            return this;
        }
    }
}
