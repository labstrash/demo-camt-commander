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
    CAMT052B,
    CAMT052BT,
    CAMT053S,
    CAMT053E,
    CAMT054D,
    CAMT054C
}
