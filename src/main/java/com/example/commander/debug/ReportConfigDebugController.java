package com.example.commander.debug;

import com.example.commander.domain.config.AccountAssignmentRow;
import com.example.commander.domain.config.AgreementScopeRow;
import com.example.commander.domain.config.AliasAssignmentRow;
import com.example.commander.domain.config.PaymentTypeAssignmentRow;
import com.example.commander.domain.config.RecipientRow;
import com.example.commander.domain.config.ReportConfigRow;
import com.example.commander.domain.config.ReportConfigTree;
import com.example.commander.repository.ReportConfigRepository;
import com.example.commander.repository.ReportConfigTreeRepository;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manual verification endpoints for {@link ReportConfigRepository} and
 * {@link ReportConfigTreeRepository} against a live database.
 *
 * <p>Every method on both repositories is reachable here, one endpoint each, so each can be
 * exercised directly and its response inspected. This is a development aid, not a
 * product-facing API — no request validation, pagination limits, or error-shape
 * conventions beyond a plain 404 on empty {@code Optional}s. Remove or gate behind a profile
 * before this application is ever exposed outside local development.
 */
@RestController
public class ReportConfigDebugController {

    private final ReportConfigRepository reportConfigRepository;
    private final ReportConfigTreeRepository reportConfigTreeRepository;

    public ReportConfigDebugController(
            ReportConfigRepository reportConfigRepository, ReportConfigTreeRepository reportConfigTreeRepository) {
        this.reportConfigRepository = reportConfigRepository;
        this.reportConfigTreeRepository = reportConfigTreeRepository;
    }

    // -------------------------------------------------------------------
    // ReportConfigRepository
    // -------------------------------------------------------------------

    @GetMapping("/debug/recipients/by-type-value")
    public ResponseEntity<RecipientRow> findRecipientByTypeAndValue(
            @RequestParam String type, @RequestParam String value) {
        return reportConfigRepository
                .findRecipientByTypeAndValue(type, value)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/debug/recipients/{id}")
    public ResponseEntity<RecipientRow> findRecipientById(@PathVariable long id) {
        return reportConfigRepository
                .findRecipientById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/debug/configs/active")
    public ResponseEntity<ReportConfigRow> findActiveByRecipientAndReportType(
            @RequestParam long recipientId, @RequestParam String reportType) {
        return reportConfigRepository
                .findActiveByRecipientAndReportType(recipientId, reportType)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // -------------------------------------------------------------------
    // ReportConfigTreeRepository
    // -------------------------------------------------------------------

    @GetMapping("/debug/configs/page")
    public List<ReportConfigRow> findConfigPage(
            @RequestParam String reportType,
            @RequestParam String reportFrequency,
            @RequestParam(defaultValue = "0") long lastSeenId,
            @RequestParam(defaultValue = "50") int pageSize) {
        return reportConfigTreeRepository.findConfigPage(reportType, reportFrequency, lastSeenId, pageSize);
    }

    @GetMapping("/debug/scopes")
    public List<AgreementScopeRow> findScopesByConfigIds(@RequestParam List<Long> configIds) {
        return reportConfigTreeRepository.findScopesByConfigIds(configIds);
    }

    @GetMapping("/debug/assignments")
    public List<PaymentTypeAssignmentRow> findAssignmentsByScopeIds(@RequestParam List<Long> scopeIds) {
        return reportConfigTreeRepository.findAssignmentsByScopeIds(scopeIds);
    }

    @GetMapping("/debug/accounts")
    public List<AccountAssignmentRow> findAccountsByAssignmentIds(@RequestParam List<Long> assignmentIds) {
        return reportConfigTreeRepository.findAccountsByAssignmentIds(assignmentIds);
    }

    @GetMapping("/debug/aliases")
    public List<AliasAssignmentRow> findAliasesByAssignmentIds(@RequestParam List<Long> assignmentIds) {
        return reportConfigTreeRepository.findAliasesByAssignmentIds(assignmentIds);
    }

    /**
     * Combines {@code findConfigPage} + {@code assembleTrees} — the only way to exercise
     * {@code assembleTrees} here, since it takes already-fetched {@code ReportConfigRow}s
     * rather than raw IDs.
     */
    @GetMapping("/debug/trees")
    public List<ReportConfigTree> findTrees(
            @RequestParam String reportType,
            @RequestParam String reportFrequency,
            @RequestParam(defaultValue = "0") long lastSeenId,
            @RequestParam(defaultValue = "50") int pageSize) {
        List<ReportConfigRow> page =
                reportConfigTreeRepository.findConfigPage(reportType, reportFrequency, lastSeenId, pageSize);
        return reportConfigTreeRepository.assembleTrees(page);
    }
}
