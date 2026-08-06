-- =============================================================================
-- SCRIPT: insert-agreement-testdata.sql
-- PURPOSE: Ad-hoc test-data insert — one Agreement and every table underneath
--          it, down to a ReportConfig linked back via ReportAgreementScope.
--          Mirrors delete-agreement-cascade.sql's dependency tree, top-down:
--
--            CAMT.Recipient            (message recipient for the scope)
--            CAMT.Agreement
--            ├── CAMT.AgreementContact       (optional)
--            └── CAMT.AgreementVersion       (Status = 'ACTIVE')
--                 └── CAMT.AgreementScope
--                      ├── CAMT.ReportAgreementScope  (link row)
--                      │        └── CAMT.ReportConfig
--                      └── CAMT.PaymentTypeAssignment
--                           ├── CAMT.AccountAssignment   (account-routed)
--                           └── CAMT.AliasAssignment      (alias-routed)
--
--          Only ONE of AccountAssignment / AliasAssignment is inserted per
--          PaymentTypeAssignment — mutually exclusive, same invariant as
--          CAMT.PaymentTypeAssignment / PaymentTypeAllocation elsewhere in
--          this codebase. Toggle @UseAliasRouting in STEP 0 to choose which.
--
-- USAGE:   1. Edit the STEP 0 variables below (all in one place).
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
--          adds, including the ReportConfig row (once it has no other
--          scope pointing at it).
--
-- REFERENCE CODES (see 01-schema-reference.sql for the full lists):
--   ReportType:      CAMT052B, CAMT052BT, CAMT053S, CAMT053E, CAMT054D, CAMT054C
--   ReportFrequency: SNAPSHOT, EVERY_30_MIN, EVERY_1_HOUR, EVERY_2_HOURS,
--                     EVERY_4_HOURS, DAILY, ONE_TIME_PER_DAY,
--                     FOUR_TIMES_PER_DAY, EIGHT_TIMES_PER_DAY
--   PaymentType:     ALL, CREDIT_TRANSFER, DIRECT_DEBIT, INSTANT_PAYMENT,
--                     ALIAS_PAYMENT
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

-- Agreement / Recipient identity
DECLARE @AgreementId     NVARCHAR(50)  = N'AGR-TESTDATA-0001';
DECLARE @AgreementName   NVARCHAR(35)  = N'Test Data Agreement';
DECLARE @EngagementBank  NVARCHAR(5)   = N'8999';
DECLARE @EngagementId    NVARCHAR(15)  = N'091111111111';
DECLARE @Channel         NVARCHAR(100) = N'Customer Portal';
DECLARE @Track           NVARCHAR(15)  = N'STANDARD';

DECLARE @RecipientType   NVARCHAR(20)  = N'ORIGINATOR';
DECLARE @RecipientValue  NVARCHAR(100) = N'TESTDATA-0001';
DECLARE @RecipientName   NVARCHAR(100) = N'Test Data Recipient';

-- Optional contact — set @AddContact = 0 to skip CAMT.AgreementContact entirely
DECLARE @AddContact      BIT           = 1;
DECLARE @ContactName     NVARCHAR(40)  = N'Test Contact';
DECLARE @ContactEmail    NVARCHAR(50)  = N'testdata@example.com';
DECLARE @ContactPhone    NVARCHAR(20)  = N'0700000000';

-- Scope / payment routing
DECLARE @ScopeName       NVARCHAR(80)  = N'Test Data Scope';
DECLARE @ReportType      NVARCHAR(40)  = N'CAMT054C';
DECLARE @PaymentType     NVARCHAR(40)  = N'CREDIT_TRANSFER';

-- Account- vs alias-routed: 0 = one AccountAssignment row (default),
-- 1 = one AliasAssignment row instead. Mutually exclusive per PTA.
DECLARE @UseAliasRouting BIT           = 0;
DECLARE @ClearingNumber  NVARCHAR(5)   = N'89011';
DECLARE @AccountNumber   NVARCHAR(9)   = N'500000001';
DECLARE @AccountBBAN     NVARCHAR(15)  = N'89011500000001';
DECLARE @Currency        NVARCHAR(3)   = N'SEK';
DECLARE @AliasId         NVARCHAR(15)  = N'99998888';

-- ReportConfig — ConfigId must be unique across CAMT.ReportConfig
-- (UX_ReportConfig_ConfigId); 08-seed-data.sql uses 10000001-10000208, so
-- staying in the 99000000+ range keeps this script's test data unambiguous.
DECLARE @ConfigId               INT           = 99000001;
DECLARE @ReportVersion          NVARCHAR(3)   = N'V02';
DECLARE @ReportFrequency        NVARCHAR(35)  = N'DAILY';
DECLARE @ConfigDescription      NVARCHAR(80)  = N'Ad-hoc test data config';
DECLARE @AccountFormat          NVARCHAR(4)   = N'IBAN';
DECLARE @IsActive               BIT           = 1;
DECLARE @IsPaginated            BIT           = 0;
DECLARE @IsEmptyReportAllowed   BIT           = 1;
DECLARE @IsBundled              BIT           = 0;

PRINT '========================================';
PRINT 'INSERT AGREEMENT TEST DATA';
PRINT 'MODE: ' + CASE WHEN @CommitChanges = 1 THEN 'COMMIT (changes will be applied)' ELSE 'DRY RUN (no changes will be applied)' END;
PRINT 'START TIME: ' + CAST(GETDATE() AS VARCHAR);
PRINT '========================================';
PRINT '';

BEGIN TRY
BEGIN TRANSACTION;

    -- =========================================================================
    -- STEP 1: Guard against clobbering existing rows
    -- =========================================================================
    IF EXISTS (SELECT 1 FROM CAMT.Agreement WHERE Id = @AgreementId)
        RAISERROR('ERROR: CAMT.Agreement.Id = %s already exists — pick a different @AgreementId.', 16, 1, @AgreementId);

    IF EXISTS (SELECT 1 FROM CAMT.ReportConfig WHERE ConfigId = @ConfigId)
        RAISERROR('ERROR: CAMT.ReportConfig.ConfigId = %d already exists — pick a different @ConfigId.', 16, 1, @ConfigId);

    IF EXISTS (SELECT 1 FROM CAMT.Recipient WHERE Type = @RecipientType AND Value = @RecipientValue)
        RAISERROR('ERROR: CAMT.Recipient (Type, Value) = (%s, %s) already exists — pick a different @RecipientValue.', 16, 1, @RecipientType, @RecipientValue);

    -- =========================================================================
    -- STEP 2: Insert top-down
    -- =========================================================================
    DECLARE @RecipientId BIGINT, @AgreementVersionId BIGINT, @AgreementScopeId BIGINT,
            @PtaId BIGINT, @ReportConfigId BIGINT;

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

    PRINT '>>> Inserting AgreementScope (ACTIVE)...';
    INSERT INTO CAMT.AgreementScope
        (AgreementVersionId, Name, MessageRecipientId, ReportType, Status, CreatedAt, ActivatedAt, CreatedBy)
    VALUES
        (@AgreementVersionId, @ScopeName, @RecipientId, @ReportType, N'ACTIVE', SYSDATETIME(), SYSDATETIME(), N'testdata-script');
    SET @AgreementScopeId = SCOPE_IDENTITY();
    PRINT '  ✓ AgreementScope.Id = ' + CAST(@AgreementScopeId AS VARCHAR);
    PRINT '';

    PRINT '>>> Inserting PaymentTypeAssignment...';
    INSERT INTO CAMT.PaymentTypeAssignment (AgreementScopeId, PaymentType, CreatedAt, CreatedBy)
    VALUES (@AgreementScopeId, @PaymentType, SYSDATETIME(), N'testdata-script');
    SET @PtaId = SCOPE_IDENTITY();
    PRINT '  ✓ PaymentTypeAssignment.Id = ' + CAST(@PtaId AS VARCHAR);
    PRINT '';

    IF @UseAliasRouting = 1
    BEGIN
        PRINT '>>> Inserting AliasAssignment...';
        INSERT INTO CAMT.AliasAssignment (PaymentTypeAssignmentId, AliasId, CreatedAt, CreatedBy)
        VALUES (@PtaId, @AliasId, SYSDATETIME(), N'testdata-script');
        PRINT '  ✓ AliasAssignment inserted (AliasId=' + @AliasId + ').';
        PRINT '';
    END
    ELSE
    BEGIN
        PRINT '>>> Inserting AccountAssignment...';
        INSERT INTO CAMT.AccountAssignment
            (PaymentTypeAssignmentId, ClearingNumber, AccountNumber, AccountBBAN, Currency, CreatedAt, CreatedBy)
        VALUES
            (@PtaId, @ClearingNumber, @AccountNumber, @AccountBBAN, @Currency, SYSDATETIME(), N'testdata-script');
        PRINT '  ✓ AccountAssignment inserted (AccountNumber=' + @AccountNumber + ').';
        PRINT '';
    END

    PRINT '>>> Inserting ReportConfig...';
    INSERT INTO CAMT.ReportConfig
        (ConfigId, ReportType, ReportVersion, ReportFrequency, Description, MessageRecipientId,
         AccountFormat, IsActive, IsPaginated, IsEmptyReportAllowed, IsBundled, CreatedAt, CreatedBy)
    VALUES
        (@ConfigId, @ReportType, @ReportVersion, @ReportFrequency, @ConfigDescription, @RecipientId,
         @AccountFormat, @IsActive, @IsPaginated, @IsEmptyReportAllowed, @IsBundled, SYSDATETIME(), N'testdata-script');
    SET @ReportConfigId = SCOPE_IDENTITY();
    PRINT '  ✓ ReportConfig.Id = ' + CAST(@ReportConfigId AS VARCHAR) + ' (ConfigId=' + CAST(@ConfigId AS VARCHAR) + ')';
    PRINT '';

    PRINT '>>> Inserting ReportAgreementScope (link)...';
    INSERT INTO CAMT.ReportAgreementScope (ReportConfigId, AgreementScopeId, CreatedAt, CreatedBy)
    VALUES (@ReportConfigId, @AgreementScopeId, SYSDATETIME(), N'testdata-script');
    PRINT '  ✓ ReportAgreementScope linked ReportConfig ' + CAST(@ReportConfigId AS VARCHAR) + ' <-> AgreementScope ' + CAST(@AgreementScopeId AS VARCHAR);
    PRINT '';

    -- =========================================================================
    -- STEP 3: Summary
    -- =========================================================================
    PRINT '>>> Summary of inserted rows:';
    PRINT '  Recipient.Id             = ' + CAST(@RecipientId AS VARCHAR);
    PRINT '  Agreement.Id             = ' + @AgreementId;
    PRINT '  AgreementVersion.Id      = ' + CAST(@AgreementVersionId AS VARCHAR);
    PRINT '  AgreementScope.Id        = ' + CAST(@AgreementScopeId AS VARCHAR);
    PRINT '  PaymentTypeAssignment.Id = ' + CAST(@PtaId AS VARCHAR);
    PRINT '  ReportConfig.Id          = ' + CAST(@ReportConfigId AS VARCHAR) + ' (ConfigId=' + CAST(@ConfigId AS VARCHAR) + ')';
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
