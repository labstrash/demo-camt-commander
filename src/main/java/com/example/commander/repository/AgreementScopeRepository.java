package com.example.commander.repository;

import com.example.commander.domain.message.ReportType;
import java.util.Optional;

/**
 * Resolves a recipient from an external engagement identifier, via {@code CAMT.Agreement} →
 * its active {@code CAMT.AgreementVersion} → the matching {@code CAMT.AgreementScope}.
 *
 * <p>Separate from {@link ReportConfigRepository} — that repository's lookups start from a
 * {@code (recipientType, recipientValue)}/{@code (recipientId, reportType)} pair already in
 * hand; this one exists to derive a recipient in the first place from an engagement
 * identifier an external system supplies (the PHT balance use case's {@code accountOwner}).
 * Once a {@code messageRecipientId} comes back from here, resolution continues through {@link
 * ReportConfigRepository#findActiveByRecipientAndReportType} exactly as the on-demand path
 * does — this repository's only job is bridging engagement identity to recipient identity.
 */
public interface AgreementScopeRepository {

    /**
     * Finds the {@code MessageRecipientId} of the active agreement scope for {@code
     * engagementId} and {@code reportType}.
     *
     * <p>"Active" means: the agreement's current {@code AgreementVersion} has {@code
     * Status = 'ACTIVE'}, and the resolved {@code AgreementScope} matches {@code reportType}.
     *
     * @param engagementId the external engagement identifier ({@code CAMT.Agreement.EngagementId})
     * @param reportType the report type the scope must match
     * @return the recipient ID if found, or empty if no match
     */
    Optional<Long> findActiveMessageRecipientId(String engagementId, ReportType reportType);
}
