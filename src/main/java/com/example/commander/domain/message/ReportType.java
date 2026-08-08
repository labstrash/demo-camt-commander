package com.example.commander.domain.message;

/**
 * The CAMT report types Commander can generate.
 *
 * <p>Each constant's name is the exact {@code Code} stored in the {@code CAMT.ReportType}
 * reference table and referenced by {@code CAMT.ReportConfig.ReportType} / {@code
 * CAMT.AgreementScope.ReportType} (both {@code NVARCHAR(40)}, FK-constrained against that
 * table). Round-trips via {@link #name()} / {@link #valueOf(String)} at JDBC, JSON, and
 * {@code @ConfigurationProperties} binding boundaries.
 */
public enum ReportType {
    CAMT052B("52B"),
    CAMT052BT("52BT"),
    CAMT053S("53S"),
    CAMT053E("53E"),
    CAMT054D("54D"),
    CAMT054C("54C");

    private final String code;

    ReportType(String code) {
        this.code = code;
    }

    /**
     * The short code embedded in generated report message IDs — see {@link
     * com.example.commander.domain.message.ReportMessageIdGenerator}. Explicit per constant
     * rather than derived, so adding a report type forces a deliberate choice of its code
     * instead of relying on every constant name being long enough for a fixed-offset substring.
     */
    public String code() {
        return code;
    }
}
