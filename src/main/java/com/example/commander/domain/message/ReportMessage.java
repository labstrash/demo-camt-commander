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
        int reportId,
        ReportType type,
        String version,
        Instant startDateTimeUtc,
        Instant endDateTimeUtc,
        boolean bundled,
        String accountFormat,
        boolean isPaginated,
        boolean isEmptyReportAllowed,
        TriggerType triggerType,
        Recipient recipient,
        List<PaymentTypeAllocation> paymentTypes,
        String requestorName,
        String correlationId,
        String id) {
    public ReportMessage {
        validateReportId(reportId);
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(version, "version cannot be null");
        Objects.requireNonNull(startDateTimeUtc, "startDateTimeUtc cannot be null");
        Objects.requireNonNull(endDateTimeUtc, "endDateTimeUtc cannot be null");
        Objects.requireNonNull(accountFormat, "accountFormat cannot be null");
        Objects.requireNonNull(triggerType, "triggerType cannot be null");
        Objects.requireNonNull(recipient, "recipient cannot be null");
        Objects.requireNonNull(correlationId, "correlationId cannot be null");
        Objects.requireNonNull(id, "id cannot be null");

        paymentTypes = paymentTypes == null ? List.of() : List.copyOf(paymentTypes);

        if (startDateTimeUtc.isAfter(endDateTimeUtc)) {
            throw new IllegalArgumentException("startDateTimeUtc must be before endDateTimeUtc");
        }
    }

    private static void validateReportId(int reportId) {
        if (reportId <= 0) {
            throw new IllegalArgumentException("reportId must be positive");
        }
    }

    /**
     * Returns the number of distinct payment types included in this message.
     *
     * @return payment type count
     */
    public int paymentTypeCount() {
        return paymentTypes.size();
    }

    /**
     * Returns the number of account assignments in this message, across all payment types.
     *
     * @return account count
     */
    public int totalAccountAssignments() {
        return paymentTypes.stream()
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
        return paymentTypes.isEmpty();
    }

    /**
     * Returns true if this message contains any payment type allocations.
     *
     * @return true if there are payment type allocations
     */
    public boolean hasPaymentTypes() {
        return !paymentTypes.isEmpty();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Integer reportId;
        private ReportType type;
        private String version;
        private Instant windowStartUtc;
        private Instant windowEndUtc;
        private Boolean bundled;
        private String accountFormat;
        private Boolean isPaginated;
        private Boolean isEmptyReportAllowed;
        private TriggerType triggerType;
        private Recipient recipient;
        private List<PaymentTypeAllocation> paymentTypeAllocations;
        private String requestorName;
        private String correlationId;
        private String id;

        private Builder() {}

        public Builder reportId(int reportId) {
            this.reportId = reportId;
            return this;
        }

        public Builder type(ReportType type) {
            this.type = type;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
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

        public Builder accountFormat(String accountFormat) {
            this.accountFormat = accountFormat;
            return this;
        }

        public Builder isPaginated(boolean isPaginated) {
            this.isPaginated = isPaginated;
            return this;
        }

        public Builder isEmptyReportAllowed(boolean isEmptyReportAllowed) {
            this.isEmptyReportAllowed = isEmptyReportAllowed;
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

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public ReportMessage build() {
            if (reportId == null) {
                throw new IllegalStateException("reportId is required");
            }
            if (type == null) {
                throw new IllegalStateException("type is required");
            }
            if (version == null) {
                throw new IllegalStateException("version is required");
            }
            if (windowStartUtc == null) {
                throw new IllegalStateException("startDateTimeUtc is required");
            }
            if (windowEndUtc == null) {
                throw new IllegalStateException("endDateTimeUtc is required");
            }
            if (bundled == null) {
                throw new IllegalStateException("bundled is required");
            }
            if (accountFormat == null) {
                throw new IllegalStateException("accountFormat is required");
            }
            if (isPaginated == null) {
                throw new IllegalStateException("isPaginated is required");
            }
            if (isEmptyReportAllowed == null) {
                throw new IllegalStateException("isEmptyReportAllowed is required");
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
            if (id == null) {
                throw new IllegalStateException("id is required");
            }

            return new ReportMessage(
                    reportId,
                    type,
                    version,
                    windowStartUtc,
                    windowEndUtc,
                    bundled,
                    accountFormat,
                    isPaginated,
                    isEmptyReportAllowed,
                    triggerType,
                    recipient,
                    paymentTypeAllocations,
                    requestorName,
                    correlationId,
                    id);
        }

        public Builder from(ReportMessage existing) {
            this.reportId = existing.reportId();
            this.type = existing.type();
            this.version = existing.version();
            this.windowStartUtc = existing.startDateTimeUtc();
            this.windowEndUtc = existing.endDateTimeUtc();
            this.bundled = existing.bundled();
            this.accountFormat = existing.accountFormat();
            this.isPaginated = existing.isPaginated();
            this.isEmptyReportAllowed = existing.isEmptyReportAllowed();
            this.triggerType = existing.triggerType();
            this.recipient = existing.recipient();
            this.paymentTypeAllocations = existing.paymentTypes();
            this.requestorName = existing.requestorName();
            this.correlationId = existing.correlationId();
            this.id = existing.id();
            return this;
        }
    }
}
