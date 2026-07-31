package com.example.commander.domain.audit;

/**
 * Status of one {@code CAMT.ReportCommandAudit} row — one row per send attempt, or, for the
 * two {@code REJECTED_*} values, one row per on-demand request that never reached a send
 * attempt at all.
 */
public enum ReportCommandAuditStatus {

    /** The message was successfully delivered to MQ. */
    SENT,

    /** The attempt failed — see {@code error_message} for which outcome and why. */
    FAILED,

    /**
     * The attempt was skipped before ever reaching MQ, because a row with this
     * {@code correlation_id} already has {@code status = SENT} — the dedup pre-check
     * caught it. Still recorded, not silently dropped, so a correctly-skipped duplicate
     * firing is visible rather than indistinguishable from "nothing happened."
     */
    SKIPPED_DUPLICATE,

    /**
     * An on-demand request was rejected because no active {@code ReportConfig} could be
     * resolved for the requested recipient/report type — either the recipient itself didn't
     * resolve, or the recipient resolved but has no matching (active) config.
     */
    REJECTED_CONFIG_NOT_ELIGIBLE,

    /**
     * An on-demand request resolved a config, but the derived reporting window wasn't valid —
     * its end lies after "now", meaning the underlying source data can't be final yet.
     */
    REJECTED_INVALID_WINDOW
}
