package com.example.commander.repository;

import com.example.commander.domain.config.RecipientRow;
import com.example.commander.domain.config.ReportConfigRow;
import java.util.Optional;

/**
 * Repository for single-row lookups used in on-demand report generation.
 *
 * <p>Provides direct lookups for recipients and report configurations by their
 * business keys. This is separate from {@link ReportConfigTreeRepository}
 * which handles staged hierarchical reads for batch processing.
 *
 * <p>{@link #findActiveByRecipientAndReportType} backs the on-demand trigger path's
 * recipient/config resolution, filtering to {@code IsActive = 1} to match the scheduled
 * path's eligibility bar. {@link #findRecipientById} is called from the scheduled path's
 * recipient-resolution processor.
 */
public interface ReportConfigRepository {

    /**
     * Finds a recipient by type and value.
     *
     * @param type recipient type (e.g., ORIGINATOR, BIC — validated by the {@code MessageRecipientType} enum)
     * @param value delivery address or endpoint
     * @return the recipient if found, or empty if no match
     */
    Optional<RecipientRow> findRecipientByTypeAndValue(String type, String value);

    /**
     * Finds a recipient by its surrogate ID.
     *
     * @param id recipient ID (as stored on {@code ReportConfig.MessageRecipientId})
     * @return the recipient if found, or empty if no match
     */
    Optional<RecipientRow> findRecipientById(long id);

    /**
     * Finds an active report configuration by recipient and report type.
     *
     * <p>Filters to {@code IsActive = 1} — same eligibility bar the scheduled path enforces —
     * so an on-demand request against a deactivated config resolves to empty rather than
     * proceeding as if it were still eligible.
     *
     * @param messageRecipientId ID of the message recipient
     * @param reportType type of report to generate
     * @return the active report configuration if found, or empty if no match
     */
    Optional<ReportConfigRow> findActiveByRecipientAndReportType(long messageRecipientId, String reportType);
}
