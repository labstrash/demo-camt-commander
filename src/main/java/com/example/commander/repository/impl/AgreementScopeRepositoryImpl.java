package com.example.commander.repository.impl;

import com.example.commander.domain.message.ReportType;
import com.example.commander.repository.AgreementScopeRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC implementation of {@link AgreementScopeRepository}.
 *
 * <p>Queries by {@code EngagementId} alone, not the schema's indexed {@code (EngagementBank,
 * EngagementId)} pair — the calling integration (PHT) supplies only a single engagement
 * identifier with no separate bank code. Neither of {@code CAMT.Agreement}'s indexes
 * guarantees uniqueness on {@code EngagementId} alone; {@link JdbcRepositorySupport#singleRow}
 * throws if this assumption turns out to be wrong for real data, rather than silently picking
 * one row.
 */
@Repository
public class AgreementScopeRepositoryImpl implements AgreementScopeRepository {

    private static final String FIND_ACTIVE_MESSAGE_RECIPIENT_ID_SQL = """
            SELECT s.MessageRecipientId
            FROM CAMT.Agreement a
            JOIN CAMT.AgreementVersion v ON v.AgreementId = a.Id AND v.Status = 'ACTIVE'
            JOIN CAMT.AgreementScope s ON s.AgreementVersionId = v.Id AND s.ReportType = ?
            WHERE a.EngagementId = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public AgreementScopeRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<Long> findActiveMessageRecipientId(String engagementId, ReportType reportType) {
        List<Long> rows = jdbcTemplate.query(
                FIND_ACTIVE_MESSAGE_RECIPIENT_ID_SQL,
                (rs, rowNum) -> rs.getLong("MessageRecipientId"),
                reportType.name(),
                engagementId);
        return JdbcRepositorySupport.singleRow(
                rows,
                "EngagementId is not guaranteed unique alone; an EngagementBank filter may be needed for this"
                        + " data");
    }
}
