-- =============================================================================
-- SCRIPT: delete-agreement-cascade.sql
-- PURPOSE: Ad-hoc cleanup — delete one or more Agreement records and every
--          dependent row underneath them, in FK-safe order:
--
--            Agreement
--            ├── AgreementContact
--            └── AgreementVersion
--                 └── AgreementScope
--                      ├── ReportAgreementScope (link row)
--                      │        └── ReportConfig  — deleted ONLY if, once
--                      │            this link is removed, it has zero
--                      │            remaining ReportAgreementScope rows.
--                      │            A ReportConfig CAN be shared across
--                      │            scopes on other, unrelated agreements
--                      │            (the "multi-scope fan-in" case) — if so,
--                      │            it is left in place so those other
--                      │            agreements keep working. See STEP 3/4.
--                      └── PaymentTypeAssignment
--                           ├── AccountAssignment
--                           └── AliasAssignment
--
--          CAMT.Recipient is a shared lookup table (also referenced directly
--          by ReportConfig) and is intentionally NEVER deleted by this script.
--
--          CAMT.DeadLetterMessage.report_config_id and
--          CAMT.ReportCommandAudit.report_config_id have no FK constraint, so
--          deleting a ReportConfig those audit rows point to will NOT error —
--          it just leaves a dangling historical reference. The script prints
--          a warning (STEP 3) if this applies so you can decide whether that's
--          acceptable before committing.
--
-- USAGE:   1. Edit the @AgreementIds list in STEP 0 below.
--          2. Run the whole script in one go (SSMS / sqlcmd / etc).
--          3. Review the PRINT output — it reports what was found, what was
--             deleted, and (if COMMIT_CHANGES = 0) makes no changes at all.
--
-- SAFETY:  Set @CommitChanges = 0 to do a dry run: every "to be deleted" count
--          is printed but the transaction is rolled back at the end, so
--          nothing is actually removed. Set it to 1 to apply the deletes.
-- =============================================================================

USE [REPORTDB];
GO

SET QUOTED_IDENTIFIER ON;
GO
SET NOCOUNT ON;
GO

DECLARE @CommitChanges BIT = 0; -- 0 = dry run (default, safe), 1 = actually delete

-- =========================================================================
-- STEP 0: EDIT ME — the Agreement.Id values to delete
-- =========================================================================
DECLARE @AgreementIds TABLE (AgreementId NVARCHAR(50) NOT NULL PRIMARY KEY);

INSERT INTO @AgreementIds (AgreementId) VALUES
    ('AGR-0001'),
    ('AGR-0002');
    -- add / remove rows as needed

PRINT '========================================';
PRINT 'DELETE AGREEMENT CASCADE';
PRINT 'MODE: ' + CASE WHEN @CommitChanges = 1 THEN 'COMMIT (changes will be applied)' ELSE 'DRY RUN (no changes will be applied)' END;
PRINT 'START TIME: ' + CAST(GETDATE() AS VARCHAR);
PRINT '========================================';
PRINT '';

BEGIN TRY
BEGIN TRANSACTION;

    -- =========================================================================
    -- STEP 1: Warn about any requested ids that don't exist
    -- =========================================================================
    IF EXISTS (
        SELECT 1 FROM @AgreementIds ai
        WHERE NOT EXISTS (SELECT 1 FROM CAMT.Agreement a WHERE a.Id = ai.AgreementId)
    )
    BEGIN
        PRINT '⚠ WARNING: The following requested AgreementIds were not found and will be skipped:';
        SELECT '  - ' + AgreementId
        FROM @AgreementIds ai
        WHERE NOT EXISTS (SELECT 1 FROM CAMT.Agreement a WHERE a.Id = ai.AgreementId);
        PRINT '';
    END

    -- =========================================================================
    -- STEP 2: Resolve the full dependency tree (top-down) into table variables
    -- =========================================================================
    DECLARE @VersionIds TABLE (Id BIGINT NOT NULL PRIMARY KEY);
    INSERT INTO @VersionIds (Id)
    SELECT v.Id
    FROM CAMT.AgreementVersion v
    WHERE v.AgreementId IN (SELECT AgreementId FROM @AgreementIds);

    DECLARE @ScopeIds TABLE (Id BIGINT NOT NULL PRIMARY KEY);
    INSERT INTO @ScopeIds (Id)
    SELECT s.Id
    FROM CAMT.AgreementScope s
    WHERE s.AgreementVersionId IN (SELECT Id FROM @VersionIds);

    DECLARE @PtaIds TABLE (Id BIGINT NOT NULL PRIMARY KEY);
    INSERT INTO @PtaIds (Id)
    SELECT p.Id
    FROM CAMT.PaymentTypeAssignment p
    WHERE p.AgreementScopeId IN (SELECT Id FROM @ScopeIds);

    -- ReportConfig candidates: every config linked (via ReportAgreementScope)
    -- to one of the target scopes. Whether each one is actually deleted is
    -- decided later — only once it has no OTHER scope (possibly on an
    -- unrelated agreement) still pointing at it.
    DECLARE @ReportConfigIds TABLE (Id BIGINT NOT NULL PRIMARY KEY);
    INSERT INTO @ReportConfigIds (Id)
    SELECT DISTINCT ras.ReportConfigId
    FROM CAMT.ReportAgreementScope ras
    WHERE ras.AgreementScopeId IN (SELECT Id FROM @ScopeIds);

    -- Of those candidates, the ones that will become orphaned (zero remaining
    -- links to ANY scope) once our target scopes' links are removed.
    DECLARE @ReportConfigOrphanIds TABLE (Id BIGINT NOT NULL PRIMARY KEY);
    INSERT INTO @ReportConfigOrphanIds (Id)
    SELECT rc.Id
    FROM @ReportConfigIds rc
    WHERE NOT EXISTS (
        SELECT 1 FROM CAMT.ReportAgreementScope ras
        WHERE ras.ReportConfigId = rc.Id
          AND ras.AgreementScopeId NOT IN (SELECT Id FROM @ScopeIds)
    );

    -- =========================================================================
    -- STEP 3: Report what will be deleted
    -- =========================================================================
    DECLARE
        @CntAgreement INT, @CntAgreementContact INT, @CntAgreementVersion INT,
        @CntAgreementScope INT, @CntReportAgreementScope INT, @CntPta INT,
        @CntAccountAssignment INT, @CntAliasAssignment INT,
        @CntReportConfigCandidates INT, @CntReportConfigOrphaned INT, @CntReportConfigShared INT,
        @CntDeadLetterAffected INT, @CntAuditAffected INT;

    SELECT @CntAgreement = COUNT(*) FROM CAMT.Agreement WHERE Id IN (SELECT AgreementId FROM @AgreementIds);
    SELECT @CntAgreementContact = COUNT(*) FROM CAMT.AgreementContact WHERE AgreementId IN (SELECT AgreementId FROM @AgreementIds);
    SELECT @CntAgreementVersion = COUNT(*) FROM @VersionIds;
    SELECT @CntAgreementScope = COUNT(*) FROM @ScopeIds;
    SELECT @CntReportAgreementScope = COUNT(*) FROM CAMT.ReportAgreementScope WHERE AgreementScopeId IN (SELECT Id FROM @ScopeIds);
    SELECT @CntPta = COUNT(*) FROM @PtaIds;
    SELECT @CntAccountAssignment = COUNT(*) FROM CAMT.AccountAssignment WHERE PaymentTypeAssignmentId IN (SELECT Id FROM @PtaIds);
    SELECT @CntAliasAssignment = COUNT(*) FROM CAMT.AliasAssignment WHERE PaymentTypeAssignmentId IN (SELECT Id FROM @PtaIds);
    SELECT @CntReportConfigCandidates = COUNT(*) FROM @ReportConfigIds;
    SELECT @CntReportConfigOrphaned = COUNT(*) FROM @ReportConfigOrphanIds;
    SET @CntReportConfigShared = @CntReportConfigCandidates - @CntReportConfigOrphaned;
    SELECT @CntDeadLetterAffected = COUNT(*) FROM CAMT.DeadLetterMessage WHERE report_config_id IN (SELECT Id FROM @ReportConfigOrphanIds);
    SELECT @CntAuditAffected = COUNT(*) FROM CAMT.ReportCommandAudit WHERE report_config_id IN (SELECT Id FROM @ReportConfigOrphanIds);

    PRINT '>>> Rows found for the requested agreement(s):';
    PRINT '  Agreement:                    ' + CAST(@CntAgreement AS VARCHAR);
    PRINT '  AgreementContact:             ' + CAST(@CntAgreementContact AS VARCHAR);
    PRINT '  AgreementVersion:             ' + CAST(@CntAgreementVersion AS VARCHAR);
    PRINT '  AgreementScope:               ' + CAST(@CntAgreementScope AS VARCHAR);
    PRINT '  ReportAgreementScope:         ' + CAST(@CntReportAgreementScope AS VARCHAR);
    PRINT '  PaymentTypeAssignment:        ' + CAST(@CntPta AS VARCHAR);
    PRINT '  AccountAssignment:            ' + CAST(@CntAccountAssignment AS VARCHAR);
    PRINT '  AliasAssignment:              ' + CAST(@CntAliasAssignment AS VARCHAR);
    PRINT '  ReportConfig (candidates):    ' + CAST(@CntReportConfigCandidates AS VARCHAR);
    PRINT '    - will be deleted (orphaned): ' + CAST(@CntReportConfigOrphaned AS VARCHAR);
    PRINT '    - will be KEPT (still shared with another agreement''s scope): ' + CAST(@CntReportConfigShared AS VARCHAR);
    PRINT '';

    IF @CntDeadLetterAffected > 0 OR @CntAuditAffected > 0
    BEGIN
        PRINT '⚠ WARNING: deleting these ReportConfig rows will leave dangling references';
        PRINT '  (no FK enforces this, so no error, but the historical link is lost):';
        PRINT '  - DeadLetterMessage rows pointing at a to-be-deleted ReportConfig: ' + CAST(@CntDeadLetterAffected AS VARCHAR);
        PRINT '  - ReportCommandAudit rows pointing at a to-be-deleted ReportConfig: ' + CAST(@CntAuditAffected AS VARCHAR);
        PRINT '';
    END

    -- =========================================================================
    -- STEP 4: Delete bottom-up (leaves first, Agreement last)
    -- =========================================================================
    DECLARE @Deleted INT;

    DELETE FROM CAMT.AliasAssignment WHERE PaymentTypeAssignmentId IN (SELECT Id FROM @PtaIds);
    SET @Deleted = @@ROWCOUNT;
    PRINT '>>> Deleted ' + CAST(@Deleted AS VARCHAR) + ' row(s) from CAMT.AliasAssignment.';

    DELETE FROM CAMT.AccountAssignment WHERE PaymentTypeAssignmentId IN (SELECT Id FROM @PtaIds);
    SET @Deleted = @@ROWCOUNT;
    PRINT '>>> Deleted ' + CAST(@Deleted AS VARCHAR) + ' row(s) from CAMT.AccountAssignment.';

    DELETE FROM CAMT.PaymentTypeAssignment WHERE Id IN (SELECT Id FROM @PtaIds);
    SET @Deleted = @@ROWCOUNT;
    PRINT '>>> Deleted ' + CAST(@Deleted AS VARCHAR) + ' row(s) from CAMT.PaymentTypeAssignment.';

    DELETE FROM CAMT.ReportAgreementScope WHERE AgreementScopeId IN (SELECT Id FROM @ScopeIds);
    SET @Deleted = @@ROWCOUNT;
    PRINT '>>> Deleted ' + CAST(@Deleted AS VARCHAR) + ' row(s) from CAMT.ReportAgreementScope.';

    -- Only delete ReportConfig rows that are now orphaned (no remaining link
    -- to ANY scope). @ReportConfigOrphanIds was computed in STEP 2 based on
    -- links that existed BEFORE the delete above, so this is safe even though
    -- the deletion just happened.
    DELETE FROM CAMT.ReportConfig WHERE Id IN (SELECT Id FROM @ReportConfigOrphanIds);
    SET @Deleted = @@ROWCOUNT;
    PRINT '>>> Deleted ' + CAST(@Deleted AS VARCHAR) + ' row(s) from CAMT.ReportConfig (orphaned only; configs still shared with other agreements'' scopes were kept).';

    DELETE FROM CAMT.AgreementScope WHERE Id IN (SELECT Id FROM @ScopeIds);
    SET @Deleted = @@ROWCOUNT;
    PRINT '>>> Deleted ' + CAST(@Deleted AS VARCHAR) + ' row(s) from CAMT.AgreementScope.';

    DELETE FROM CAMT.AgreementVersion WHERE Id IN (SELECT Id FROM @VersionIds);
    SET @Deleted = @@ROWCOUNT;
    PRINT '>>> Deleted ' + CAST(@Deleted AS VARCHAR) + ' row(s) from CAMT.AgreementVersion.';

    DELETE FROM CAMT.AgreementContact WHERE AgreementId IN (SELECT AgreementId FROM @AgreementIds);
    SET @Deleted = @@ROWCOUNT;
    PRINT '>>> Deleted ' + CAST(@Deleted AS VARCHAR) + ' row(s) from CAMT.AgreementContact.';

    DELETE FROM CAMT.Agreement WHERE Id IN (SELECT AgreementId FROM @AgreementIds);
    SET @Deleted = @@ROWCOUNT;
    PRINT '>>> Deleted ' + CAST(@Deleted AS VARCHAR) + ' row(s) from CAMT.Agreement.';
    PRINT '';

    -- =========================================================================
    -- STEP 5: Validation — none of the requested agreements (or their
    -- descendants) should remain
    -- =========================================================================
    IF EXISTS (SELECT 1 FROM CAMT.Agreement WHERE Id IN (SELECT AgreementId FROM @AgreementIds))
        RAISERROR('ERROR: Some requested Agreement rows still exist after delete!', 16, 1);

    IF EXISTS (SELECT 1 FROM CAMT.AgreementVersion WHERE Id IN (SELECT Id FROM @VersionIds))
        RAISERROR('ERROR: Some AgreementVersion rows still exist after delete!', 16, 1);

    IF EXISTS (SELECT 1 FROM CAMT.AgreementScope WHERE Id IN (SELECT Id FROM @ScopeIds))
        RAISERROR('ERROR: Some AgreementScope rows still exist after delete!', 16, 1);

    IF EXISTS (SELECT 1 FROM CAMT.PaymentTypeAssignment WHERE Id IN (SELECT Id FROM @PtaIds))
        RAISERROR('ERROR: Some PaymentTypeAssignment rows still exist after delete!', 16, 1);

    IF EXISTS (SELECT 1 FROM CAMT.ReportAgreementScope WHERE AgreementScopeId IN (SELECT Id FROM @ScopeIds))
        RAISERROR('ERROR: Some ReportAgreementScope rows still exist after delete!', 16, 1);

    IF EXISTS (SELECT 1 FROM CAMT.ReportConfig WHERE Id IN (SELECT Id FROM @ReportConfigOrphanIds))
        RAISERROR('ERROR: Some ReportConfig rows expected to be orphaned still exist after delete!', 16, 1);

    -- NOTE: CAMT.ReportConfig rows in @ReportConfigIds but NOT in
    -- @ReportConfigOrphanIds are expected to still exist — they're shared
    -- with a scope on another agreement and are deliberately kept.

    PRINT '✓ Validation complete: no residual rows for the requested agreement(s).';
    PRINT '';

    IF @CommitChanges = 1
    BEGIN
        COMMIT TRANSACTION;
        PRINT '========================================';
        PRINT '✓ CHANGES COMMITTED';
    END
    ELSE
    BEGIN
        ROLLBACK TRANSACTION;
        PRINT '========================================';
        PRINT 'ℹ DRY RUN — transaction rolled back, no changes were applied.';
        PRINT 'ℹ Set @CommitChanges = 1 at the top of this script to apply the deletes.';
    END

    PRINT 'END TIME: ' + CAST(GETDATE() AS VARCHAR);
    PRINT '========================================';

END TRY
BEGIN CATCH
    IF @@TRANCOUNT > 0
        ROLLBACK TRANSACTION;

    PRINT '';
    PRINT '========================================';
    PRINT '✗ SCRIPT FAILED!';
    PRINT 'ERROR: ' + ERROR_MESSAGE();
    PRINT 'ERROR NUMBER: ' + CAST(ERROR_NUMBER() AS VARCHAR);
    PRINT 'ERROR LINE: ' + CAST(ERROR_LINE() AS VARCHAR);
    PRINT '========================================';

    THROW;
END CATCH
GO
