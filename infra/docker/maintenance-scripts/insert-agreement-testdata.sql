-- =============================================================================
-- SCRIPT: insert-agreement-testdata.sql
-- PURPOSE: Ad-hoc test-data insert — one Agreement, with as many
--          AgreementScope / PaymentTypeAssignment / AccountAssignment /
--          AliasAssignment rows underneath it as you configure below, each
--          scope linked to its own ReportConfig via ReportAgreementScope.
--
--            CAMT.Recipient            (one recipient, shared by every scope)
--            CAMT.Agreement
--            ├── CAMT.AgreementContact       (optional)
--            └── CAMT.AgreementVersion       (Status = 'ACTIVE')
--                 └── CAMT.AgreementScope           (one row per @Scopes entry)
--                      ├── CAMT.ReportAgreementScope  (link row)
--                      │        └── CAMT.ReportConfig  (one row per @Scopes
--                      │            entry, same ReportType as its scope)
--                      └── CAMT.PaymentTypeAssignment  (one row per
--                           │                          @PaymentTypeAssignments entry)
--                           ├── CAMT.AccountAssignment   (0+ rows, from @Accounts)
--                           └── CAMT.AliasAssignment      (0+ rows, from @Aliases)
--
--          Mutually exclusive per PTA: a (ScopeSeq, PtaSeq) pair may have rows
--          in @Accounts OR @Aliases, never both — same invariant as
--          CAMT.PaymentTypeAssignment / PaymentTypeAllocation elsewhere in this
--          codebase (STEP 1 rejects the insert if violated).
--
--          One recipient and one AgreementVersion cover every scope, so every
--          @Scopes entry needs a DISTINCT ReportType — CAMT.AgreementScope's
--          own uniqueness (AgreementVersionId, MessageRecipientId, ReportType)
--          would otherwise reject a second scope with the same type.
--
-- USAGE:   1. Edit the STEP 0 variables and table variables below (all in one
--             place). ScopeSeq / PtaSeq are keys YOU assign (any distinct
--             integers) — they don't exist in the database until STEP 2 runs,
--             so @PaymentTypeAssignments/@Accounts/@Aliases reference a scope
--             or PTA by this key rather than a real row ID.
--          2. Run the whole script in one go (SSMS / sqlcmd / etc).
--          3. Review the PRINT output.
--          4. Set @CommitChanges = 1 and re-run to actually apply the insert
--             (defaults to a dry run so nothing is written by accident).
--
-- SAFETY:  Set @CommitChanges = 0 to do a dry run: every row that would be
--          inserted is printed but the transaction is rolled back at the
--          end, so nothing is actually written. Set it to 1 to apply.
--
-- CLEANUP: The inserted Agreement.Id is exactly @AgreementId below — pass
--          that same value to delete-agreement-cascade.sql's @AgreementIds
--          list in this same directory to remove everything this script
--          adds, including every ReportConfig row it created (once each has
--          no other scope pointing at it).
--
-- REFERENCE CODES (see 01-schema-reference.sql for the full lists):
--   ReportType:      CAMT052B, CAMT052BT, CAMT053S, CAMT053E, CAMT054D, CAMT054C
--   ReportFrequency: EVERY_30_MIN, EVERY_1_HOUR, EVERY_2_HOURS,
--                     EVERY_4_HOURS, DAILY, ONE_TIME_PER_DAY,
--                     FOUR_TIMES_PER_DAY, EIGHT_TIMES_PER_DAY
--   PaymentType:     ALL, INCALIAS, INSTDOM
-- =============================================================================

USE [REPORTDB];
GO

SET QUOTED_IDENTIFIER ON;
GO
SET NOCOUNT ON;
GO

DECLARE @CommitChanges BIT = 0; -- 0 = dry run (default, safe), 1 = actually insert

-- =========================================================================
-- STEP 0: EDIT ME
-- =========================================================================

-- Agreement / Recipient identity (one of each, shared by every scope below)
DECLARE @AgreementId     NVARCHAR(50)  = N'AGR-TESTDATA-0001';
DECLARE @AgreementName   NVARCHAR(35)  = N'Test Data Agreement';
DECLARE @EngagementBank  NVARCHAR(5)   = N'8999';
DECLARE @EngagementId    NVARCHAR(15)  = N'091111111111';
DECLARE @Channel         NVARCHAR(100) = N'Customer Portal';
DECLARE @Track           NVARCHAR(15)  = N'STANDARD';

DECLARE @RecipientType   NVARCHAR(20)  = N'SIGNER_ID';
DECLARE @RecipientValue  NVARCHAR(100) = N'TESTDATA-0001';
DECLARE @RecipientName   NVARCHAR(100) = N'Test Data Recipient';

-- Optional contact — set @AddContact = 0 to skip CAMT.AgreementContact entirely
DECLARE @AddContact      BIT           = 1;
DECLARE @ContactName     NVARCHAR(40)  = N'Test Contact';
DECLARE @ContactEmail    NVARCHAR(50)  = N'testdata@example.com';
DECLARE @ContactPhone    NVARCHAR(20)  = N'0700000000';

-- ---------------------------------------------------------------------
-- Scopes — one row per AgreementScope, each with its own ReportConfig.
-- ConfigId must be unique across CAMT.ReportConfig (UX_ReportConfig_ConfigId)
-- — 08-seed-data.sql uses 10000001-10000208, so staying in the 99000000+
-- range keeps this script's test data unambiguous.
-- ---------------------------------------------------------------------
DECLARE @Scopes TABLE (
    ScopeSeq             INT          NOT NULL PRIMARY KEY,
    ScopeName            NVARCHAR(80) NULL,
    ReportType           NVARCHAR(40) NOT NULL,
    ConfigId             INT          NOT NULL,
    ReportVersion        NVARCHAR(3)  NOT NULL,
    ReportFrequency      NVARCHAR(35) NOT NULL,
    ConfigDescription    NVARCHAR(80) NOT NULL,
    AccountFormat        NVARCHAR(4)  NOT NULL,
    IsActive             BIT          NOT NULL,
    IsPaginated          BIT          NOT NULL,
    IsEmptyReportAllowed BIT          NOT NULL,
    IsBundled            BIT          NOT NULL
);
INSERT INTO @Scopes
    (ScopeSeq, ScopeName, ReportType, ConfigId, ReportVersion, ReportFrequency, ConfigDescription, AccountFormat, IsActive, IsPaginated, IsEmptyReportAllowed, IsBundled)
VALUES
    (1, N'Test Data Scope 1', N'CAMT054C', 99000001, N'V02', N'FOUR_TIMES_PER_DAY', N'Ad-hoc test data config 1', N'IBAN', 1, 0, 1, 0),
    (2, N'Test Data Scope 2', N'CAMT052B', 99000002, N'V02', N'EVERY_2_HOURS', N'Ad-hoc test data config 2', N'BBAN', 1, 0, 1, 1);
    -- add / remove rows as needed — one row = one AgreementScope + one ReportConfig

-- ---------------------------------------------------------------------
-- Payment type assignments — one row per PaymentTypeAssignment, keyed by
-- the ScopeSeq it belongs to plus a PtaSeq you assign (unique per scope).
-- ---------------------------------------------------------------------
DECLARE @PaymentTypeAssignments TABLE (
    ScopeSeq    INT          NOT NULL,
    PtaSeq      INT          NOT NULL,
    PaymentType NVARCHAR(40) NOT NULL,
    PRIMARY KEY (ScopeSeq, PtaSeq)
);
INSERT INTO @PaymentTypeAssignments (ScopeSeq, PtaSeq, PaymentType)
VALUES
    (1, 1, N'INSTDOM'),
    (1, 2, N'INCALIAS'),
    (2, 1, N'ALL');
    -- add / remove rows as needed

-- ---------------------------------------------------------------------
-- Accounts — 0+ rows per (ScopeSeq, PtaSeq). A PTA routed via accounts
-- must have NO rows in @Aliases below (mutually exclusive, checked in
-- STEP 1).
-- ---------------------------------------------------------------------
DECLARE @Accounts TABLE (
    ScopeSeq       INT          NOT NULL,
    PtaSeq         INT          NOT NULL,
    ClearingNumber NVARCHAR(5)  NOT NULL,
    AccountNumber  NVARCHAR(10)  NOT NULL,
    AccountBBAN    NVARCHAR(15) NOT NULL,
    Currency       NVARCHAR(3)  NOT NULL
);
INSERT INTO @Accounts (ScopeSeq, PtaSeq, ClearingNumber, AccountNumber, AccountBBAN, Currency)
VALUES
    (1, 1, N'89011', N'500000001', N'89011500000001', N'SEK'),
    (1, 1, N'83279', N'500000002', N'83279500000002', N'SEK'),
    (1, 2, N'89011', N'500000003', N'89011500000003', N'SEK');
    -- add / remove rows as needed

-- ---------------------------------------------------------------------
-- Aliases — 0+ rows per (ScopeSeq, PtaSeq). A PTA routed via aliases
-- must have NO rows in @Accounts above (mutually exclusive, checked in
-- STEP 1).
-- ---------------------------------------------------------------------
DECLARE @Aliases TABLE (
    ScopeSeq INT          NOT NULL,
    PtaSeq   INT          NOT NULL,
    AliasId  NVARCHAR(15) NOT NULL
);
INSERT INTO @Aliases (ScopeSeq, PtaSeq, AliasId)
VALUES
    (2, 1, N'99998888'),
    (2, 1, N'11112222');
    -- add / remove rows as needed (delete both rows if scope 2's PTA has no aliases)

PRINT '========================================';
PRINT 'INSERT AGREEMENT TEST DATA';
PRINT 'MODE: ' + CASE WHEN @CommitChanges = 1 THEN 'COMMIT (changes will be applied)' ELSE 'DRY RUN (no changes will be applied)' END;
PRINT 'START TIME: ' + CAST(GETDATE() AS VARCHAR);
PRINT '========================================';
PRINT '';

BEGIN TRY
BEGIN TRANSACTION;

    -- =========================================================================
    -- STEP 1: Guard against clobbering existing rows and bad edit-me data
    -- =========================================================================
    IF EXISTS (SELECT 1 FROM CAMT.Agreement WHERE Id = @AgreementId)
        RAISERROR('ERROR: CAMT.Agreement.Id = %s already exists — pick a different @AgreementId.', 16, 1, @AgreementId);

    IF EXISTS (SELECT 1 FROM CAMT.Recipient WHERE Type = @RecipientType AND Value = @RecipientValue)
        RAISERROR('ERROR: CAMT.Recipient (Type, Value) = (%s, %s) already exists — pick a different @RecipientValue.', 16, 1, @RecipientType, @RecipientValue);

    IF EXISTS (SELECT ReportType FROM @Scopes GROUP BY ReportType HAVING COUNT(*) > 1)
        RAISERROR('ERROR: @Scopes has more than one row with the same ReportType — every scope shares the same recipient/version, so ReportType must be distinct per scope (CAMT.AgreementScope''s own uniqueness would reject this).', 16, 1);

    IF EXISTS (SELECT ConfigId FROM @Scopes GROUP BY ConfigId HAVING COUNT(*) > 1)
        RAISERROR('ERROR: @Scopes has more than one row with the same ConfigId — ConfigId must be unique.', 16, 1);

    IF EXISTS (SELECT 1 FROM CAMT.ReportConfig rc JOIN @Scopes s ON s.ConfigId = rc.ConfigId)
        RAISERROR('ERROR: one or more @Scopes.ConfigId values already exist in CAMT.ReportConfig — pick different ConfigId values.', 16, 1);

    IF EXISTS (
        SELECT 1 FROM @PaymentTypeAssignments pta
        WHERE NOT EXISTS (SELECT 1 FROM @Scopes s WHERE s.ScopeSeq = pta.ScopeSeq)
    )
        RAISERROR('ERROR: @PaymentTypeAssignments references a ScopeSeq that is not in @Scopes.', 16, 1);

    IF EXISTS (
        SELECT 1 FROM @Accounts a
        WHERE NOT EXISTS (SELECT 1 FROM @PaymentTypeAssignments pta WHERE pta.ScopeSeq = a.ScopeSeq AND pta.PtaSeq = a.PtaSeq)
    )
        RAISERROR('ERROR: @Accounts references a (ScopeSeq, PtaSeq) that is not in @PaymentTypeAssignments.', 16, 1);

    IF EXISTS (
        SELECT 1 FROM @Aliases al
        WHERE NOT EXISTS (SELECT 1 FROM @PaymentTypeAssignments pta WHERE pta.ScopeSeq = al.ScopeSeq AND pta.PtaSeq = al.PtaSeq)
    )
        RAISERROR('ERROR: @Aliases references a (ScopeSeq, PtaSeq) that is not in @PaymentTypeAssignments.', 16, 1);

    IF EXISTS (
        SELECT 1 FROM @Accounts a
        WHERE EXISTS (SELECT 1 FROM @Aliases al WHERE al.ScopeSeq = a.ScopeSeq AND al.PtaSeq = a.PtaSeq)
    )
        RAISERROR('ERROR: at least one (ScopeSeq, PtaSeq) has rows in BOTH @Accounts and @Aliases — mutually exclusive, same invariant as PaymentTypeAllocation.', 16, 1);

    -- =========================================================================
    -- STEP 2: Insert top-down
    -- =========================================================================
    DECLARE @RecipientId BIGINT, @AgreementVersionId BIGINT;
    DECLARE @ScopeIdMap TABLE (ScopeSeq INT PRIMARY KEY, AgreementScopeId BIGINT);
    DECLARE @PtaIdMap TABLE (ScopeSeq INT, PtaSeq INT, PaymentTypeAssignmentId BIGINT, PRIMARY KEY (ScopeSeq, PtaSeq));
    DECLARE @ConfigIdMap TABLE (ScopeSeq INT PRIMARY KEY, ReportConfigId BIGINT);

    DECLARE @ScopeCount INT, @PtaCount INT, @AccountCount INT, @AliasCount INT;
    SELECT @ScopeCount = COUNT(*) FROM @Scopes;
    SELECT @PtaCount = COUNT(*) FROM @PaymentTypeAssignments;
    SELECT @AccountCount = COUNT(*) FROM @Accounts;
    SELECT @AliasCount = COUNT(*) FROM @Aliases;

    PRINT '>>> Inserting AgreementSequence...';
    IF NOT EXISTS (SELECT 1 FROM CAMT.AgreementSequence WHERE EngagementId = @EngagementId)
    BEGIN
        INSERT INTO CAMT.AgreementSequence (EngagementId, NextVal) VALUES (@EngagementId, 1);
        PRINT '  ✓ Seeded AgreementSequence for EngagementId=' + @EngagementId;
    END
    ELSE
        PRINT '  - AgreementSequence for EngagementId=' + @EngagementId + ' already present, left as-is.';
    PRINT '';

    PRINT '>>> Inserting Recipient...';
    INSERT INTO CAMT.Recipient (Type, Value, Name, CreatedAt, CreatedBy)
    VALUES (@RecipientType, @RecipientValue, @RecipientName, SYSDATETIME(), N'testdata-script');
    SET @RecipientId = SCOPE_IDENTITY();
    PRINT '  ✓ Recipient.Id = ' + CAST(@RecipientId AS VARCHAR);
    PRINT '';

    PRINT '>>> Inserting Agreement...';
    INSERT INTO CAMT.Agreement
        (Id, Name, EngagementBank, EngagementId, Channel, Track, StartDate, CreatedAt, CreatedBy)
    VALUES
        (@AgreementId, @AgreementName, @EngagementBank, @EngagementId, @Channel, @Track, SYSDATETIME(), SYSDATETIME(), N'testdata-script');
    PRINT '  ✓ Agreement.Id = ' + @AgreementId;
    PRINT '';

    IF @AddContact = 1
    BEGIN
        PRINT '>>> Inserting AgreementContact...';
        INSERT INTO CAMT.AgreementContact
            (AgreementId, ContactName, ContactEmail, ContactPhone, CreatedAt, CreatedBy)
        VALUES
            (@AgreementId, @ContactName, @ContactEmail, @ContactPhone, SYSDATETIME(), N'testdata-script');
        PRINT '  ✓ AgreementContact inserted for ' + @AgreementId;
        PRINT '';
    END

    PRINT '>>> Inserting AgreementVersion (ACTIVE)...';
    INSERT INTO CAMT.AgreementVersion
        (VersionId, AgreementId, Status, CreatedAt, ActivatedAt, CreatedBy, Version)
    VALUES
        (CAST(NEWID() AS NVARCHAR(50)), @AgreementId, N'ACTIVE', SYSDATETIME(), SYSDATETIME(), N'testdata-script', 0);
    SET @AgreementVersionId = SCOPE_IDENTITY();
    PRINT '  ✓ AgreementVersion.Id = ' + CAST(@AgreementVersionId AS VARCHAR);
    PRINT '';

    PRINT '>>> Inserting AgreementScope rows (' + CAST(@ScopeCount AS VARCHAR) + ')...';
    MERGE INTO CAMT.AgreementScope AS tgt
    USING @Scopes AS src
    ON 1 = 0
    WHEN NOT MATCHED THEN
        INSERT (AgreementVersionId, Name, MessageRecipientId, ReportType, Status, CreatedAt, ActivatedAt, CreatedBy)
        VALUES (@AgreementVersionId, src.ScopeName, @RecipientId, src.ReportType, N'ACTIVE', SYSDATETIME(), SYSDATETIME(), N'testdata-script')
    OUTPUT src.ScopeSeq, inserted.Id INTO @ScopeIdMap (ScopeSeq, AgreementScopeId);
    PRINT '  ✓ ' + CAST(@@ROWCOUNT AS VARCHAR) + ' AgreementScope row(s) inserted.';
    PRINT '';

    PRINT '>>> Inserting PaymentTypeAssignment rows (' + CAST(@PtaCount AS VARCHAR) + ')...';
    MERGE INTO CAMT.PaymentTypeAssignment AS tgt
    USING (
        SELECT pta.ScopeSeq, pta.PtaSeq, pta.PaymentType, sm.AgreementScopeId
        FROM @PaymentTypeAssignments pta
        JOIN @ScopeIdMap sm ON sm.ScopeSeq = pta.ScopeSeq
    ) AS src
    ON 1 = 0
    WHEN NOT MATCHED THEN
        INSERT (AgreementScopeId, PaymentType, CreatedAt, CreatedBy)
        VALUES (src.AgreementScopeId, src.PaymentType, SYSDATETIME(), N'testdata-script')
    OUTPUT src.ScopeSeq, src.PtaSeq, inserted.Id INTO @PtaIdMap (ScopeSeq, PtaSeq, PaymentTypeAssignmentId);
    PRINT '  ✓ ' + CAST(@@ROWCOUNT AS VARCHAR) + ' PaymentTypeAssignment row(s) inserted.';
    PRINT '';

    PRINT '>>> Inserting AccountAssignment rows (' + CAST(@AccountCount AS VARCHAR) + ')...';
    INSERT INTO CAMT.AccountAssignment
        (PaymentTypeAssignmentId, ClearingNumber, AccountNumber, AccountBBAN, Currency, CreatedAt, CreatedBy)
    SELECT pm.PaymentTypeAssignmentId, a.ClearingNumber, a.AccountNumber, a.AccountBBAN, a.Currency, SYSDATETIME(), N'testdata-script'
    FROM @Accounts a
    JOIN @PtaIdMap pm ON pm.ScopeSeq = a.ScopeSeq AND pm.PtaSeq = a.PtaSeq;
    PRINT '  ✓ ' + CAST(@@ROWCOUNT AS VARCHAR) + ' AccountAssignment row(s) inserted.';
    PRINT '';

    PRINT '>>> Inserting AliasAssignment rows (' + CAST(@AliasCount AS VARCHAR) + ')...';
    INSERT INTO CAMT.AliasAssignment (PaymentTypeAssignmentId, AliasId, CreatedAt, CreatedBy)
    SELECT pm.PaymentTypeAssignmentId, al.AliasId, SYSDATETIME(), N'testdata-script'
    FROM @Aliases al
    JOIN @PtaIdMap pm ON pm.ScopeSeq = al.ScopeSeq AND pm.PtaSeq = al.PtaSeq;
    PRINT '  ✓ ' + CAST(@@ROWCOUNT AS VARCHAR) + ' AliasAssignment row(s) inserted.';
    PRINT '';

    PRINT '>>> Inserting ReportConfig rows (' + CAST(@ScopeCount AS VARCHAR) + ')...';
    MERGE INTO CAMT.ReportConfig AS tgt
    USING @Scopes AS src
    ON 1 = 0
    WHEN NOT MATCHED THEN
        INSERT (ConfigId, ReportType, ReportVersion, ReportFrequency, Description, MessageRecipientId,
                AccountFormat, IsActive, IsPaginated, IsEmptyReportAllowed, IsBundled, CreatedAt, CreatedBy)
        VALUES (src.ConfigId, src.ReportType, src.ReportVersion, src.ReportFrequency, src.ConfigDescription, @RecipientId,
                src.AccountFormat, src.IsActive, src.IsPaginated, src.IsEmptyReportAllowed, src.IsBundled, SYSDATETIME(), N'testdata-script')
    OUTPUT src.ScopeSeq, inserted.Id INTO @ConfigIdMap (ScopeSeq, ReportConfigId);
    PRINT '  ✓ ' + CAST(@@ROWCOUNT AS VARCHAR) + ' ReportConfig row(s) inserted.';
    PRINT '';

    PRINT '>>> Inserting ReportAgreementScope (link) rows...';
    INSERT INTO CAMT.ReportAgreementScope (ReportConfigId, AgreementScopeId, CreatedAt, CreatedBy)
    SELECT cm.ReportConfigId, sm.AgreementScopeId, SYSDATETIME(), N'testdata-script'
    FROM @ConfigIdMap cm
    JOIN @ScopeIdMap sm ON sm.ScopeSeq = cm.ScopeSeq;
    PRINT '  ✓ ' + CAST(@@ROWCOUNT AS VARCHAR) + ' ReportAgreementScope row(s) inserted.';
    PRINT '';

    -- =========================================================================
    -- STEP 3: Summary
    -- =========================================================================
    PRINT '>>> Summary:';
    PRINT '  Recipient.Id        = ' + CAST(@RecipientId AS VARCHAR);
    PRINT '  Agreement.Id        = ' + @AgreementId;
    PRINT '  AgreementVersion.Id = ' + CAST(@AgreementVersionId AS VARCHAR);
    PRINT '';

    PRINT '  Scopes (ScopeSeq -> AgreementScope.Id, ReportConfig.Id / ConfigId):';
    SELECT
        s.ScopeSeq,
        sm.AgreementScopeId,
        s.ReportType,
        cm.ReportConfigId,
        s.ConfigId
    FROM @Scopes s
    JOIN @ScopeIdMap sm ON sm.ScopeSeq = s.ScopeSeq
    JOIN @ConfigIdMap cm ON cm.ScopeSeq = s.ScopeSeq
    ORDER BY s.ScopeSeq;

    PRINT '  Payment type assignments (ScopeSeq, PtaSeq -> PaymentTypeAssignment.Id):';
    SELECT ScopeSeq, PtaSeq, PaymentTypeAssignmentId FROM @PtaIdMap ORDER BY ScopeSeq, PtaSeq;
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
        PRINT 'ℹ Set @CommitChanges = 1 at the top of this script to apply the insert.';
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
