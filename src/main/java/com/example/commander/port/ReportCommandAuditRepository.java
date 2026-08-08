package com.example.commander.port;

import com.example.commander.domain.audit.ReportCommandAuditEntry;
import java.time.Instant;

/**
 * Repository for {@code CAMT.ReportCommandAudit} — one row per send attempt, written by
 * {@code MqReportMessageWriter} (primary send) and {@code DeadLetterRecoveryJob} (recovery
 * resend), the only two places a {@code ResilientMqSender.send()} outcome is observed.
 */
public interface ReportCommandAuditRepository {

    /**
     * Inserts one audit row.
     *
     * @param entry the row to insert
     */
    void insert(ReportCommandAuditEntry entry);

    /**
     * Deletes up to {@code limit} rows with {@code sent_at} older than {@code cutoff} —
     * bounded, so a large backlog can't turn one retention firing into one long-held table
     * lock. {@code AuditRetentionJob} calls this repeatedly per firing until a call returns
     * fewer than {@code limit}, meaning the backlog for this firing is exhausted.
     *
     * @param cutoff rows with {@code sent_at} strictly before this are eligible
     * @param limit maximum rows to delete in this call
     * @return the number of rows actually deleted
     */
    int deleteOlderThan(Instant cutoff, int limit);
}
