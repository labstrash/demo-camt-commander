package com.example.commander.adapter.persistence;

import com.example.commander.domain.audit.ReportCommandAuditEntry;
import com.example.commander.port.ReportCommandAuditRepository;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Calendar;
import java.util.TimeZone;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC implementation of {@link ReportCommandAuditRepository}.
 * All {@code DATETIME2} columns are bound/read with an explicit UTC calendar to avoid
 * JVM default timezone conversions.
 */
@Repository
@RequiredArgsConstructor
public class ReportCommandAuditRepositoryImpl implements ReportCommandAuditRepository {

    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    private static final String INSERT_SQL = """
            INSERT INTO CAMT.ReportCommandAudit
                (message_id, correlation_id, report_config_id, config_id, agreement_scope_id,
                 report_type, report_version, report_frequency, trigger_type,
                 window_start_utc, window_end_utc, is_bundled, account_count, payment_type_count,
                 recipient_type, recipient_value, mq_queue_name, mq_message_id,
                 status, error_message, sent_at, retry_count,
                 job_execution_id, step_execution_id, requestor_name)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    // DELETE TOP requires a literal; limit is a controlled property, safe from injection.
    private static final String DELETE_OLDER_THAN_SQL_TEMPLATE =
            "DELETE TOP (%d) FROM CAMT.ReportCommandAudit WHERE sent_at < ?";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void insert(ReportCommandAuditEntry entry) {
        jdbcTemplate.update(INSERT_SQL, ps -> {
            int i = 1;
            ps.setString(i++, entry.messageId());
            ps.setString(i++, entry.correlationId());
            setNullableLong(ps, i++, entry.reportConfigId());
            ps.setString(i++, entry.configId());
            setNullableLong(ps, i++, entry.agreementScopeId());
            ps.setString(i++, entry.reportType().name());
            ps.setString(i++, entry.reportVersion());
            ps.setString(i++, entry.reportFrequency());
            ps.setString(i++, entry.triggerType().name());
            ps.setTimestamp(i++, Timestamp.from(entry.windowStartUtc()), Calendar.getInstance(UTC));
            ps.setTimestamp(i++, Timestamp.from(entry.windowEndUtc()), Calendar.getInstance(UTC));
            ps.setBoolean(i++, entry.isBundled());
            ps.setInt(i++, entry.accountCount());
            ps.setInt(i++, entry.paymentTypeCount());
            ps.setString(i++, entry.recipientType());
            ps.setString(i++, entry.recipientValue());
            ps.setString(i++, entry.mqQueueName());
            ps.setString(i++, entry.mqMessageId());
            ps.setString(i++, entry.status().name());
            ps.setString(i++, entry.errorMessage());
            ps.setTimestamp(i++, Timestamp.from(entry.sentAt()), Calendar.getInstance(UTC));
            ps.setInt(i++, entry.retryCount());
            setNullableLong(ps, i++, entry.jobExecutionId());
            setNullableLong(ps, i++, entry.stepExecutionId());
            ps.setString(i, entry.requestorName());
        });
    }

    @Override
    public int deleteOlderThan(Instant cutoff, int limit) {
        String sql = DELETE_OLDER_THAN_SQL_TEMPLATE.formatted(limit);
        return jdbcTemplate.update(sql, ps -> ps.setTimestamp(1, Timestamp.from(cutoff), Calendar.getInstance(UTC)));
    }

    private static void setNullableLong(PreparedStatement ps, int index, Long value) throws SQLException {
        if (value != null) {
            ps.setLong(index, value);
        } else {
            ps.setNull(index, Types.BIGINT);
        }
    }
}
