package com.example.camt.contract;

/**
 * The CAMT report types {@code demo-camt-commander} can generate. Serialized on the wire as
 * the constant name (e.g. {@code "CAMT054C"}), so plain {@code Enum} deserialization is
 * sufficient — no custom converter needed.
 */
public enum ReportType {
    CAMT052B,
    CAMT052BT,
    CAMT053S,
    CAMT053E,
    CAMT054D,
    CAMT054C
}
