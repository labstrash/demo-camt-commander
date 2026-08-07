-- =============================================================================
-- SCRIPT: 08-seed-data.sql
-- PURPOSE: Seed demo/dev data.
--   Section A - 2 happy-path scenarios
--   Section B - 3 fetch-config read-path edge cases (zero-scope config,
--               dangling PaymentTypeAssignment, multi-scope fan-in)
--   Section C - 5 realistic onboarding-style scenarios (from
--               testdata_inputs.txt): single/dual-scope agreements,
--               account- and alias-routed payment types
--   Section D - a scope with 2 PaymentTypeAssignment rows, plus the 3
--               ReportFrequency codes no other seed data exercises
--   Section E - non-ACTIVE lifecycle states: version history (REPLACED ->
--               ACTIVE), pending activation (no ACTIVE version yet), and a
--               cancelled scope alongside an active one
-- EXECUTION: Run after 07-schema-fetch-config.sql
--
-- No idempotency guards (no IF NOT EXISTS) - consistent with the rest of this
-- directory's "clean deployment" approach: 00-drop-all.sql always runs first,
-- so every script here always starts from an empty CAMT schema.
--
-- VersionId values use NEWID() rather than hardcoded GUIDs, since the only
-- requirement is that they're unique (UQ_AgreementVersion_VersionId).
-- =============================================================================

USE [REPORTDB];
GO

SET QUOTED_IDENTIFIER ON;
GO

PRINT '========================================';
PRINT 'SCRIPT 08: SEED DATA';
PRINT 'START TIME: ' + CAST(GETDATE() AS VARCHAR);
PRINT '========================================';
PRINT '';

BEGIN TRY
BEGIN TRANSACTION;

    DECLARE @RecipientId1 BIGINT, @RecipientId2 BIGINT;
    DECLARE @AgreementVersionId1 BIGINT, @AgreementVersionId2 BIGINT;
    DECLARE @AgreementScopeId1 BIGINT, @AgreementScopeId2 BIGINT;
    DECLARE @PaymentTypeAssignmentId1 BIGINT, @PaymentTypeAssignmentId2 BIGINT;
    DECLARE @ReportConfigId1 BIGINT, @ReportConfigId2 BIGINT;

    DECLARE @EdgeRecipientId1 BIGINT, @EdgeRecipientId2 BIGINT, @EdgeRecipientId3 BIGINT;
    DECLARE @EdgeVersionId2 BIGINT, @EdgeVersionId3A BIGINT, @EdgeVersionId3B BIGINT;
    DECLARE @EdgeScopeId2 BIGINT, @EdgeScopeId3A BIGINT, @EdgeScopeId3B BIGINT;
    DECLARE @EdgePtaId3A BIGINT, @EdgePtaId3B BIGINT;
    DECLARE @EdgeConfigId1 BIGINT, @EdgeConfigId2 BIGINT, @EdgeConfigId3 BIGINT;

    -- Section C: realistic multi-scope scenarios
    DECLARE @CRecipientId1 BIGINT, @CRecipientId2 BIGINT, @CRecipientId3 BIGINT, @CRecipientId4 BIGINT, @CRecipientId5 BIGINT;
    DECLARE @CVersionId1 BIGINT, @CVersionId2 BIGINT, @CVersionId3 BIGINT, @CVersionId4 BIGINT, @CVersionId5 BIGINT;
    DECLARE @CScopeId1 BIGINT, @CScopeId2 BIGINT;
    DECLARE @CScopeId3A BIGINT, @CScopeId3B BIGINT, @CScopeId4A BIGINT, @CScopeId4B BIGINT, @CScopeId5A BIGINT, @CScopeId5B BIGINT;
    DECLARE @CPtaId1 BIGINT, @CPtaId2 BIGINT;
    DECLARE @CPtaId3A BIGINT, @CPtaId3B BIGINT, @CPtaId4A BIGINT, @CPtaId4B BIGINT, @CPtaId5A BIGINT, @CPtaId5B BIGINT;
    DECLARE @CConfigId1 BIGINT, @CConfigId2 BIGINT;
    DECLARE @CConfigId3A BIGINT, @CConfigId3B BIGINT, @CConfigId4A BIGINT, @CConfigId4B BIGINT, @CConfigId5A BIGINT, @CConfigId5B BIGINT;

    -- Section D: multi-payment-type scope + remaining frequency coverage
    DECLARE @DRecipientId1 BIGINT, @DRecipientId2 BIGINT, @DRecipientId3 BIGINT, @DRecipientId4 BIGINT;
    DECLARE @DVersionId1 BIGINT, @DVersionId2 BIGINT, @DVersionId3 BIGINT, @DVersionId4 BIGINT;
    DECLARE @DScopeId1 BIGINT, @DScopeId2 BIGINT, @DScopeId3 BIGINT, @DScopeId4 BIGINT;
    DECLARE @DPtaId1CT BIGINT, @DPtaId1DD BIGINT, @DPtaId2 BIGINT, @DPtaId3 BIGINT, @DPtaId4 BIGINT;
    DECLARE @DConfigId1 BIGINT, @DConfigId2 BIGINT, @DConfigId3 BIGINT, @DConfigId4 BIGINT;

    -- Section E: non-active lifecycle states
    DECLARE @ERecipientId1 BIGINT, @ERecipientId2 BIGINT, @ERecipientId3 BIGINT;
    DECLARE @EVersionId1Old BIGINT, @EVersionId1New BIGINT, @EVersionId2 BIGINT, @EVersionId3 BIGINT;
    DECLARE @EScopeId1 BIGINT, @EScopeId2 BIGINT, @EScopeId3Active BIGINT, @EScopeId3Cancelled BIGINT;
    DECLARE @EPtaId1 BIGINT, @EPtaId2 BIGINT, @EPtaId3Active BIGINT, @EPtaId3Cancelled BIGINT;
    DECLARE @EConfigId1 BIGINT, @EConfigId2 BIGINT, @EConfigId3Active BIGINT, @EConfigId3Cancelled BIGINT;

    -- =========================================================================
    -- SECTION A: HAPPY-PATH SCENARIOS
    -- Two ordinary agreements, each with one scope, one payment-type
    -- assignment, and one funded account - the everyday case the fetch/
    -- assembly path spends most of its time on.
    -- =========================================================================

    PRINT '>>> Section A: Happy-path scenarios';
    PRINT '';

    -- -------------------------------------------------------------------
    -- A.1 Agreement sequence (pre-seeds the next-value counter so
    --     GetNextAgreementSequence has a starting point for these
    --     engagement IDs)
    -- -------------------------------------------------------------------
    PRINT '  - Seeding AgreementSequence...';

INSERT INTO CAMT.AgreementSequence (EngagementId, NextVal)
VALUES
    (N'067696104012', 1),
    (N'065561959304', 1);

PRINT '  ✓ ' + CAST(@@ROWCOUNT AS VARCHAR) + ' rows inserted into AgreementSequence.';

    -- -------------------------------------------------------------------
    -- A.2 Recipients
    -- -------------------------------------------------------------------
    PRINT '  - Seeding Recipients...';

INSERT INTO CAMT.Recipient (Type, Value, Name, CreatedAt, CreatedBy)
VALUES (N'SIGNER_ID', N'3937231530REP0001', N'Team Nirvana A', SYSDATETIME(), N'seed');
SET @RecipientId1 = SCOPE_IDENTITY();

INSERT INTO CAMT.Recipient (Type, Value, Name, CreatedAt, CreatedBy)
VALUES (N'SIGNER_ID', N'767951290ORI0240', N'Team Nirvana B', SYSDATETIME(), N'seed');
SET @RecipientId2 = SCOPE_IDENTITY();

    PRINT '  ✓ 2 rows inserted into Recipient.';

    -- -------------------------------------------------------------------
    -- A.3 Agreements
    -- -------------------------------------------------------------------
    PRINT '  - Seeding Agreements...';

INSERT INTO CAMT.Agreement
(Id, Name, EngagementBank, EngagementId, Channel, Track, StartDate, CreatedAt, CreatedBy)
VALUES
    (N'393723153REP0001', N'Agreement One', N'8999', N'067696104012', N'Customer Portal', N'NOTIFICATION', SYSDATETIME(), SYSDATETIME(), N'seed'),
    (N'767951290REP0001', N'Agreement Two', N'8999', N'065561959304', N'Customer Portal', N'STANDARD',     SYSDATETIME(), SYSDATETIME(), N'seed');

PRINT '  ✓ ' + CAST(@@ROWCOUNT AS VARCHAR) + ' rows inserted into Agreement.';

    -- -------------------------------------------------------------------
    -- A.4 Agreement contact (Agreement Two only, mirroring real data
    --     where not every agreement has a contact on file)
    -- -------------------------------------------------------------------
    PRINT '  - Seeding AgreementContact...';

INSERT INTO CAMT.AgreementContact
(AgreementId, ContactName, ContactEmail, ContactPhone, CreatedAt, CreatedBy)
VALUES
    (N'767951290REP0001', N'Contact TNB', N'nirvana@example.com', N'07987654321', SYSDATETIME(), N'seed');

PRINT '  ✓ ' + CAST(@@ROWCOUNT AS VARCHAR) + ' rows inserted into AgreementContact.';

    -- -------------------------------------------------------------------
    -- A.5 Agreement versions
    -- -------------------------------------------------------------------
    PRINT '  - Seeding AgreementVersion...';

INSERT INTO CAMT.AgreementVersion
(VersionId, AgreementId, Status, CreatedAt, ActivatedAt, CreatedBy, Version)
VALUES (CAST(NEWID() AS NVARCHAR(50)), N'393723153REP0001', N'ACTIVE', SYSDATETIME(), SYSDATETIME(), N'seed', 0);
SET @AgreementVersionId1 = SCOPE_IDENTITY();

INSERT INTO CAMT.AgreementVersion
(VersionId, AgreementId, Status, CreatedAt, ActivatedAt, CreatedBy, Version)
VALUES (CAST(NEWID() AS NVARCHAR(50)), N'767951290REP0001', N'ACTIVE', SYSDATETIME(), SYSDATETIME(), N'seed', 0);
SET @AgreementVersionId2 = SCOPE_IDENTITY();

    PRINT '  ✓ 2 rows inserted into AgreementVersion.';

    -- -------------------------------------------------------------------
    -- A.6 Agreement scopes
    -- -------------------------------------------------------------------
    PRINT '  - Seeding AgreementScope...';

INSERT INTO CAMT.AgreementScope
(AgreementVersionId, Name, MessageRecipientId, ReportType, Status, CreatedAt, ActivatedAt, CreatedBy)
VALUES (@AgreementVersionId1, N'Scope One', @RecipientId1, N'CAMT054C', N'ACTIVE', SYSDATETIME(), SYSDATETIME(), N'seed');
SET @AgreementScopeId1 = SCOPE_IDENTITY();

INSERT INTO CAMT.AgreementScope
(AgreementVersionId, Name, MessageRecipientId, ReportType, Status, CreatedAt, ActivatedAt, CreatedBy)
VALUES (@AgreementVersionId2, N'Scope Two', @RecipientId2, N'CAMT052B', N'ACTIVE', SYSDATETIME(), SYSDATETIME(), N'seed');
SET @AgreementScopeId2 = SCOPE_IDENTITY();

    PRINT '  ✓ 2 rows inserted into AgreementScope.';

    -- -------------------------------------------------------------------
    -- A.7 Payment type assignments
    -- -------------------------------------------------------------------
    PRINT '  - Seeding PaymentTypeAssignment...';

INSERT INTO CAMT.PaymentTypeAssignment (AgreementScopeId, PaymentType, CreatedAt, CreatedBy)
VALUES (@AgreementScopeId1, N'INSTDOM', SYSDATETIME(), N'seed');
SET @PaymentTypeAssignmentId1 = SCOPE_IDENTITY();

INSERT INTO CAMT.PaymentTypeAssignment (AgreementScopeId, PaymentType, CreatedAt, CreatedBy)
VALUES (@AgreementScopeId2, N'ALL', SYSDATETIME(), N'seed');
SET @PaymentTypeAssignmentId2 = SCOPE_IDENTITY();

    PRINT '  ✓ 2 rows inserted into PaymentTypeAssignment.';

    -- -------------------------------------------------------------------
    -- A.8 Account assignments
    -- -------------------------------------------------------------------
    PRINT '  - Seeding AccountAssignment...';

INSERT INTO CAMT.AccountAssignment
(PaymentTypeAssignmentId, ClearingNumber, AccountNumber, AccountBBAN, Currency, CreatedAt, CreatedBy)
VALUES
    (@PaymentTypeAssignmentId1, N'89011', N'504669699', N'89011504669699', N'SEK', SYSDATETIME(), N'seed'),
    (@PaymentTypeAssignmentId2, N'83279', N'503797518', N'83279503797518', N'SEK', SYSDATETIME(), N'seed');

PRINT '  ✓ ' + CAST(@@ROWCOUNT AS VARCHAR) + ' rows inserted into AccountAssignment.';

    -- -------------------------------------------------------------------
    -- A.9 Report configs
    -- -------------------------------------------------------------------
    PRINT '  - Seeding ReportConfig...';

INSERT INTO CAMT.ReportConfig
(ConfigId, ReportType, ReportVersion, ReportFrequency, Description, MessageRecipientId, AccountFormat, IsActive, IsPaginated, IsEmptyReportAllowed, IsBundled, CreatedAt, CreatedBy)
VALUES (10000001, N'CAMT054C', N'V02', N'FOUR_TIMES_PER_DAY', N'Report Config One', @RecipientId1, N'IBAN', 1, 0, 1, 0, SYSDATETIME(), N'seed');
SET @ReportConfigId1 = SCOPE_IDENTITY();

INSERT INTO CAMT.ReportConfig
(ConfigId, ReportType, ReportVersion, ReportFrequency, Description, MessageRecipientId, AccountFormat, IsActive, IsPaginated, IsEmptyReportAllowed, IsBundled, CreatedAt, CreatedBy)
VALUES (10000002, N'CAMT052B', N'V02', N'EVERY_30_MIN', N'Report Config Two', @RecipientId2, N'BBAN', 1, 1, 0, 1, SYSDATETIME(), N'seed');
SET @ReportConfigId2 = SCOPE_IDENTITY();

    PRINT '  ✓ 2 rows inserted into ReportConfig.';

    -- -------------------------------------------------------------------
    -- A.10 Report/agreement-scope links
    -- -------------------------------------------------------------------
    PRINT '  - Seeding ReportAgreementScope...';

INSERT INTO CAMT.ReportAgreementScope (ReportConfigId, AgreementScopeId, CreatedAt, CreatedBy)
VALUES
    (@ReportConfigId1, @AgreementScopeId1, SYSDATETIME(), N'seed'),
    (@ReportConfigId2, @AgreementScopeId2, SYSDATETIME(), N'seed');

PRINT '  ✓ ' + CAST(@@ROWCOUNT AS VARCHAR) + ' rows inserted into ReportAgreementScope.';
    PRINT '✓ Section A complete.';
    PRINT '';

    -- =========================================================================
    -- SECTION B: EDGE-CASE SCENARIOS
    -- Exercises the three tree-assembly edge cases the fetch/assembly read
    -- path has to handle correctly (see ReportConfigTreeAssemblerTest):
    --   B.1 - zero-scope config      (ReportConfig with no linked scopes)
    --   B.2 - dangling PTA           (PaymentTypeAssignment with neither
    --                                 accounts nor aliases underneath it)
    --   B.3 - multi-scope fan-in     (one ReportConfig, two AgreementScope
    --                                 rows from two different agreements)
    -- =========================================================================

    PRINT '>>> Section B: Edge-case scenarios';
    PRINT '';

    -- -------------------------------------------------------------------
    -- B.1 Zero-scope config: a ReportConfig with no ReportAgreementScope
    --     rows at all. No Agreement/Scope/PTA needed - the whole point is
    --     that nothing links to this config.
    -- -------------------------------------------------------------------
    PRINT '  - B.1: Zero-scope config...';

INSERT INTO CAMT.Recipient (Type, Value, Name, CreatedAt, CreatedBy)
VALUES (N'SIGNER_ID', N'EDGE-ZERO-0001', N'Edge Case - Zero Scope Ltd', SYSDATETIME(), N'seed');
SET @EdgeRecipientId1 = SCOPE_IDENTITY();

INSERT INTO CAMT.ReportConfig
(ConfigId, ReportType, ReportVersion, ReportFrequency, Description, MessageRecipientId, AccountFormat, IsActive, IsPaginated, IsEmptyReportAllowed, IsBundled, CreatedAt, CreatedBy)
VALUES (10000101, N'CAMT054D', N'V02', N'DAILY', N'Edge case - zero-scope config (no linked scope)', @EdgeRecipientId1, N'BBAN', 1, 0, 1, 1, SYSDATETIME(), N'seed');
SET @EdgeConfigId1 = SCOPE_IDENTITY();

    PRINT '  ✓ ReportConfig ' + CAST(@EdgeConfigId1 AS VARCHAR) + ' seeded with zero linked scopes.';
    PRINT '';

    -- -------------------------------------------------------------------
    -- B.2 Dangling PTA: a PaymentTypeAssignment with no AccountAssignment
    --     and no AliasAssignment rows underneath it.
    -- -------------------------------------------------------------------
    PRINT '  - B.2: Dangling payment-type assignment...';

INSERT INTO CAMT.Recipient (Type, Value, Name, CreatedAt, CreatedBy)
VALUES (N'SIGNER_ID', N'EDGE-DANGLING-01', N'Edge Case - Dangling PTA AB', SYSDATETIME(), N'seed');
SET @EdgeRecipientId2 = SCOPE_IDENTITY();

INSERT INTO CAMT.Agreement
(Id, Name, EngagementBank, EngagementId, Channel, Track, StartDate, CreatedAt, CreatedBy)
VALUES (N'AGR-EDGE-DANGLING-0001', N'Edge Agreement - Dangling PTA', N'EDGE', N'EDGE-DANG-01', N'Customer Portal', N'STANDARD', SYSDATETIME(), SYSDATETIME(), N'seed');

INSERT INTO CAMT.AgreementVersion
(VersionId, AgreementId, Status, CreatedAt, ActivatedAt, CreatedBy, Version)
VALUES (CAST(NEWID() AS NVARCHAR(50)), N'AGR-EDGE-DANGLING-0001', N'ACTIVE', SYSDATETIME(), SYSDATETIME(), N'seed', 0);
SET @EdgeVersionId2 = SCOPE_IDENTITY();

INSERT INTO CAMT.AgreementScope
(AgreementVersionId, Name, MessageRecipientId, ReportType, Status, CreatedAt, ActivatedAt, CreatedBy)
VALUES (@EdgeVersionId2, N'Edge Scope - Dangling PTA', @EdgeRecipientId2, N'CAMT052B', N'ACTIVE', SYSDATETIME(), SYSDATETIME(), N'seed');
SET @EdgeScopeId2 = SCOPE_IDENTITY();

    -- Deliberately no AccountAssignment / AliasAssignment rows for this PTA.
INSERT INTO CAMT.PaymentTypeAssignment (AgreementScopeId, PaymentType, CreatedAt, CreatedBy)
VALUES (@EdgeScopeId2, N'INSTDOM', SYSDATETIME(), N'seed');

INSERT INTO CAMT.ReportConfig
(ConfigId, ReportType, ReportVersion, ReportFrequency, Description, MessageRecipientId, AccountFormat, IsActive, IsPaginated, IsEmptyReportAllowed, IsBundled, CreatedAt, CreatedBy)
VALUES (10000102, N'CAMT052B', N'V02', N'EVERY_2_HOURS', N'Edge case - dangling PTA (no accounts or aliases)', @EdgeRecipientId2, N'IBAN', 1, 0, 1, 0, SYSDATETIME(), N'seed');
SET @EdgeConfigId2 = SCOPE_IDENTITY();

INSERT INTO CAMT.ReportAgreementScope (ReportConfigId, AgreementScopeId, CreatedAt, CreatedBy)
VALUES (@EdgeConfigId2, @EdgeScopeId2, SYSDATETIME(), N'seed');

PRINT '  ✓ ReportConfig ' + CAST(@EdgeConfigId2 AS VARCHAR) + ' seeded with a PTA that has neither accounts nor aliases.';
    PRINT '';

    -- -------------------------------------------------------------------
    -- B.3 Multi-scope fan-in: one recipient/report-type pair (one
    --     ReportConfig) reached via scopes on two separate agreements.
    -- -------------------------------------------------------------------
    PRINT '  - B.3: Multi-scope fan-in...';

INSERT INTO CAMT.Recipient (Type, Value, Name, CreatedAt, CreatedBy)
VALUES (N'SIGNER_ID', N'EDGE-FANIN-0001', N'Edge Case - Multi-Scope Fan-In Corp', SYSDATETIME(), N'seed');
SET @EdgeRecipientId3 = SCOPE_IDENTITY();

INSERT INTO CAMT.Agreement
(Id, Name, EngagementBank, EngagementId, Channel, Track, StartDate, CreatedAt, CreatedBy)
VALUES
    (N'AGR-EDGE-FANIN-A', N'Edge Agreement - Fan-In A', N'EDGE', N'EDGE-FANIN-A', N'Customer Portal', N'STANDARD', SYSDATETIME(), SYSDATETIME(), N'seed'),
    (N'AGR-EDGE-FANIN-B', N'Edge Agreement - Fan-In B', N'EDGE', N'EDGE-FANIN-B', N'Partner API',     N'STANDARD', SYSDATETIME(), SYSDATETIME(), N'seed');

INSERT INTO CAMT.AgreementVersion
(VersionId, AgreementId, Status, CreatedAt, ActivatedAt, CreatedBy, Version)
VALUES (CAST(NEWID() AS NVARCHAR(50)), N'AGR-EDGE-FANIN-A', N'ACTIVE', SYSDATETIME(), SYSDATETIME(), N'seed', 0);
SET @EdgeVersionId3A = SCOPE_IDENTITY();

INSERT INTO CAMT.AgreementVersion
(VersionId, AgreementId, Status, CreatedAt, ActivatedAt, CreatedBy, Version)
VALUES (CAST(NEWID() AS NVARCHAR(50)), N'AGR-EDGE-FANIN-B', N'ACTIVE', SYSDATETIME(), SYSDATETIME(), N'seed', 0);
SET @EdgeVersionId3B = SCOPE_IDENTITY();

    -- Same MessageRecipientId + ReportType on both scopes is what makes
    -- these fan into a single ReportConfig via ReportAgreementScope below.
INSERT INTO CAMT.AgreementScope
(AgreementVersionId, Name, MessageRecipientId, ReportType, Status, CreatedAt, ActivatedAt, CreatedBy)
VALUES (@EdgeVersionId3A, N'Edge Scope - Fan-In A', @EdgeRecipientId3, N'CAMT054C', N'ACTIVE', SYSDATETIME(), SYSDATETIME(), N'seed');
SET @EdgeScopeId3A = SCOPE_IDENTITY();

INSERT INTO CAMT.AgreementScope
(AgreementVersionId, Name, MessageRecipientId, ReportType, Status, CreatedAt, ActivatedAt, CreatedBy)
VALUES (@EdgeVersionId3B, N'Edge Scope - Fan-In B', @EdgeRecipientId3, N'CAMT054C', N'ACTIVE', SYSDATETIME(), SYSDATETIME(), N'seed');
SET @EdgeScopeId3B = SCOPE_IDENTITY();

INSERT INTO CAMT.PaymentTypeAssignment (AgreementScopeId, PaymentType, CreatedAt, CreatedBy)
VALUES (@EdgeScopeId3A, N'INCALIAS', SYSDATETIME(), N'seed');
SET @EdgePtaId3A = SCOPE_IDENTITY();

INSERT INTO CAMT.PaymentTypeAssignment (AgreementScopeId, PaymentType, CreatedAt, CreatedBy)
VALUES (@EdgeScopeId3B, N'INSTDOM', SYSDATETIME(), N'seed');
SET @EdgePtaId3B = SCOPE_IDENTITY();

INSERT INTO CAMT.AccountAssignment
(PaymentTypeAssignmentId, ClearingNumber, AccountNumber, AccountBBAN, Currency, CreatedAt, CreatedBy)
VALUES
    (@EdgePtaId3A, N'90001', N'100000001', N'90001100000001', N'SEK', SYSDATETIME(), N'seed'),
    (@EdgePtaId3B, N'90002', N'200000002', N'90002200000002', N'SEK', SYSDATETIME(), N'seed');

INSERT INTO CAMT.ReportConfig
(ConfigId, ReportType, ReportVersion, ReportFrequency, Description, MessageRecipientId, AccountFormat, IsActive, IsPaginated, IsEmptyReportAllowed, IsBundled, CreatedAt, CreatedBy)
VALUES (10000103, N'CAMT054C', N'V02', N'ONE_TIME_PER_DAY', N'Edge case - multi-scope fan-in (2 agreements, 1 recipient/type)', @EdgeRecipientId3, N'BBAN', 1, 0, 0, 0, SYSDATETIME(), N'seed');
SET @EdgeConfigId3 = SCOPE_IDENTITY();

INSERT INTO CAMT.ReportAgreementScope (ReportConfigId, AgreementScopeId, CreatedAt, CreatedBy)
VALUES
    (@EdgeConfigId3, @EdgeScopeId3A, SYSDATETIME(), N'seed'),
    (@EdgeConfigId3, @EdgeScopeId3B, SYSDATETIME(), N'seed');

PRINT '  ✓ ReportConfig ' + CAST(@EdgeConfigId3 AS VARCHAR) + ' seeded with 2 AgreementScope rows from 2 separate agreements.';
    PRINT '';
    PRINT '✓ Section B complete.';
    PRINT '';

    -- =========================================================================
    -- SECTION C: REALISTIC MULTI-SCOPE SCENARIOS (from testdata_inputs.txt)
    -- Five onboarding-style agreements: two single-scope (account- and
    -- alias-routed payment), three dual-scope (independent report type per
    -- scope on the same agreement/version).
    --
    -- PAYMENT TYPE: source input uses "INSTDOM" and "INCALIAS" directly - both
    -- are real CAMT.PaymentType codes, seeded as-is with no translation.
    -- =========================================================================

    PRINT '>>> Section C: Realistic multi-scope scenarios';
    PRINT '';

    PRINT '  - Seeding AgreementSequence for Section C engagements...';

INSERT INTO CAMT.AgreementSequence (EngagementId, NextVal)
VALUES
    (N'061111111111', 1),
    (N'061111111112', 1),
    (N'061111111113', 1),
    (N'061111111114', 1),
    (N'061111111115', 1);

PRINT '  ✓ ' + CAST(@@ROWCOUNT AS VARCHAR) + ' rows inserted into AgreementSequence.';
    PRINT '';

    -- -------------------------------------------------------------------
    -- C.1 Scenario 1: single scope, INSTDOM, 4x/day
    -- -------------------------------------------------------------------
    PRINT '  - C.1: Scenario 1 - INSTDOM, single scope, 4x/day...';

INSERT INTO CAMT.Recipient (Type, Value, Name, CreatedAt, CreatedBy)
VALUES (N'SIGNER_ID', N'TEST-SCN-01', N'Test Recipient - Scenario 1', SYSDATETIME(), N'seed');
SET @CRecipientId1 = SCOPE_IDENTITY();

INSERT INTO CAMT.Agreement
(Id, Name, EngagementBank, EngagementId, Channel, Track, StartDate, CreatedAt, CreatedBy)
VALUES (N'AGR-TEST-SCN-01', N'Test Agmt - INSTDOM 4x/Day', N'8999', N'061111111111', N'Customer Portal', N'NOTIFICATION', SYSDATETIME(), SYSDATETIME(), N'seed');

INSERT INTO CAMT.AgreementVersion
(VersionId, AgreementId, Status, PricingOrderRef, CreatedAt, ActivatedAt, CreatedBy, Version)
VALUES (CAST(NEWID() AS NVARCHAR(50)), N'AGR-TEST-SCN-01', N'ACTIVE', N'P2B-061111111111-01', SYSDATETIME(), SYSDATETIME(), N'seed', 0);
SET @CVersionId1 = SCOPE_IDENTITY();

INSERT INTO CAMT.AgreementScope
(AgreementVersionId, Name, MessageRecipientId, ReportType, Status, CreatedAt, ActivatedAt, CreatedBy)
VALUES (@CVersionId1, N'CAMT054C - V02 - 4 TIMES', @CRecipientId1, N'CAMT054C', N'ACTIVE', SYSDATETIME(), SYSDATETIME(), N'seed');
SET @CScopeId1 = SCOPE_IDENTITY();

INSERT INTO CAMT.PaymentTypeAssignment (AgreementScopeId, PaymentType, CreatedAt, CreatedBy)
VALUES (@CScopeId1, N'INSTDOM', SYSDATETIME(), N'seed');
SET @CPtaId1 = SCOPE_IDENTITY();

INSERT INTO CAMT.AccountAssignment
(PaymentTypeAssignmentId, ClearingNumber, AccountNumber, AccountBBAN, Currency, CreatedAt, CreatedBy)
VALUES
    (@CPtaId1, N'89011', N'504669699', N'890110504669699', N'SEK', SYSDATETIME(), N'seed'),
    (@CPtaId1, N'83279', N'503797518', N'832790503797518', N'SEK', SYSDATETIME(), N'seed');

INSERT INTO CAMT.ReportConfig
(ConfigId, ReportType, ReportVersion, ReportFrequency, Description, MessageRecipientId, AccountFormat, IsActive, IsPaginated, IsEmptyReportAllowed, IsBundled, CreatedAt, CreatedBy)
VALUES (10000201, N'CAMT054C', N'V02', N'FOUR_TIMES_PER_DAY', N'Scenario 1 - INSTDOM notification, 4x/day', @CRecipientId1, N'IBAN', 1, 0, 1, 0, SYSDATETIME(), N'seed');
SET @CConfigId1 = SCOPE_IDENTITY();

INSERT INTO CAMT.ReportAgreementScope (ReportConfigId, AgreementScopeId, CreatedAt, CreatedBy)
VALUES (@CConfigId1, @CScopeId1, SYSDATETIME(), N'seed');

    PRINT '  ✓ Scenario 1 seeded.';
    PRINT '';

    -- -------------------------------------------------------------------
    -- C.2 Scenario 2: single scope, INCALIAS, 1x/day
    -- -------------------------------------------------------------------
    PRINT '  - C.2: Scenario 2 - INCALIAS, single scope, 1x/day...';

INSERT INTO CAMT.Recipient (Type, Value, Name, CreatedAt, CreatedBy)
VALUES (N'SIGNER_ID', N'TEST-SCN-02', N'Test Recipient - Scenario 2', SYSDATETIME(), N'seed');
SET @CRecipientId2 = SCOPE_IDENTITY();

INSERT INTO CAMT.Agreement
(Id, Name, EngagementBank, EngagementId, Channel, Track, StartDate, CreatedAt, CreatedBy)
VALUES (N'AGR-TEST-SCN-02', N'Test Agmt - INCALIAS 1x/Day', N'8999', N'061111111112', N'Customer Portal', N'NOTIFICATION', SYSDATETIME(), SYSDATETIME(), N'seed');

INSERT INTO CAMT.AgreementVersion
(VersionId, AgreementId, Status, PricingOrderRef, CreatedAt, ActivatedAt, CreatedBy, Version)
VALUES (CAST(NEWID() AS NVARCHAR(50)), N'AGR-TEST-SCN-02', N'ACTIVE', N'P2B-061111111112-02', SYSDATETIME(), SYSDATETIME(), N'seed', 0);
SET @CVersionId2 = SCOPE_IDENTITY();

INSERT INTO CAMT.AgreementScope
(AgreementVersionId, Name, MessageRecipientId, ReportType, Status, CreatedAt, ActivatedAt, CreatedBy)
VALUES (@CVersionId2, N'CAMT054C - V02 - 1 TIME', @CRecipientId2, N'CAMT054C', N'ACTIVE', SYSDATETIME(), SYSDATETIME(), N'seed');
SET @CScopeId2 = SCOPE_IDENTITY();

INSERT INTO CAMT.PaymentTypeAssignment (AgreementScopeId, PaymentType, CreatedAt, CreatedBy)
VALUES (@CScopeId2, N'INCALIAS', SYSDATETIME(), N'seed');
SET @CPtaId2 = SCOPE_IDENTITY();

INSERT INTO CAMT.AliasAssignment (PaymentTypeAssignmentId, AliasId, CreatedAt, CreatedBy)
VALUES
    (@CPtaId2, N'11112222', SYSDATETIME(), N'seed'),
    (@CPtaId2, N'22223333', SYSDATETIME(), N'seed');

INSERT INTO CAMT.ReportConfig
(ConfigId, ReportType, ReportVersion, ReportFrequency, Description, MessageRecipientId, AccountFormat, IsActive, IsPaginated, IsEmptyReportAllowed, IsBundled, CreatedAt, CreatedBy)
VALUES (10000202, N'CAMT054C', N'V02', N'ONE_TIME_PER_DAY', N'Scenario 2 - INCALIAS notification, 1x/day', @CRecipientId2, N'IBAN', 1, 0, 1, 0, SYSDATETIME(), N'seed');
SET @CConfigId2 = SCOPE_IDENTITY();

INSERT INTO CAMT.ReportAgreementScope (ReportConfigId, AgreementScopeId, CreatedAt, CreatedBy)
VALUES (@CConfigId2, @CScopeId2, SYSDATETIME(), N'seed');

    PRINT '  ✓ Scenario 2 seeded.';
    PRINT '';

    -- -------------------------------------------------------------------
    -- C.3 Scenario 3: dual scope - CAMT054D DAILY + CAMT052B EVERY_2_HOURS
    -- -------------------------------------------------------------------
    PRINT '  - C.3: Scenario 3 - dual scope (Daily + Every-2-Hours)...';

INSERT INTO CAMT.Recipient (Type, Value, Name, CreatedAt, CreatedBy)
VALUES (N'SIGNER_ID', N'TEST-SCN-03', N'Test Recipient - Scenario 3', SYSDATETIME(), N'seed');
SET @CRecipientId3 = SCOPE_IDENTITY();

INSERT INTO CAMT.Agreement
(Id, Name, EngagementBank, EngagementId, Channel, Track, StartDate, CreatedAt, CreatedBy)
VALUES (N'AGR-TEST-SCN-03', N'Test Agmt - Daily+Snapshot Dual', N'8999', N'061111111113', N'Customer Portal', N'STANDARD', SYSDATETIME(), SYSDATETIME(), N'seed');

INSERT INTO CAMT.AgreementVersion
(VersionId, AgreementId, Status, PricingOrderRef, CreatedAt, ActivatedAt, CreatedBy, Version)
VALUES (CAST(NEWID() AS NVARCHAR(50)), N'AGR-TEST-SCN-03', N'ACTIVE', N'P2B-061111111113-01', SYSDATETIME(), SYSDATETIME(), N'seed', 0);
SET @CVersionId3 = SCOPE_IDENTITY();

INSERT INTO CAMT.AgreementScope
(AgreementVersionId, Name, MessageRecipientId, ReportType, Status, CreatedAt, ActivatedAt, CreatedBy)
VALUES (@CVersionId3, N'CAMT054D - V02 - DAILY', @CRecipientId3, N'CAMT054D', N'ACTIVE', SYSDATETIME(), SYSDATETIME(), N'seed');
SET @CScopeId3A = SCOPE_IDENTITY();

INSERT INTO CAMT.AgreementScope
(AgreementVersionId, Name, MessageRecipientId, ReportType, Status, CreatedAt, ActivatedAt, CreatedBy)
VALUES (@CVersionId3, N'CAMT052B - V02 - EVERY_2_HOURS', @CRecipientId3, N'CAMT052B', N'ACTIVE', SYSDATETIME(), SYSDATETIME(), N'seed');
SET @CScopeId3B = SCOPE_IDENTITY();

INSERT INTO CAMT.PaymentTypeAssignment (AgreementScopeId, PaymentType, CreatedAt, CreatedBy)
VALUES (@CScopeId3A, N'ALL', SYSDATETIME(), N'seed');
SET @CPtaId3A = SCOPE_IDENTITY();

INSERT INTO CAMT.PaymentTypeAssignment (AgreementScopeId, PaymentType, CreatedAt, CreatedBy)
VALUES (@CScopeId3B, N'ALL', SYSDATETIME(), N'seed');
SET @CPtaId3B = SCOPE_IDENTITY();

INSERT INTO CAMT.AccountAssignment
(PaymentTypeAssignmentId, ClearingNumber, AccountNumber, AccountBBAN, Currency, CreatedAt, CreatedBy)
VALUES
    (@CPtaId3A, N'89011', N'504669699', N'890110504669699', N'SEK', SYSDATETIME(), N'seed'),
    (@CPtaId3A, N'83279', N'503797518', N'832790503797518', N'SEK', SYSDATETIME(), N'seed'),
    (@CPtaId3B, N'89012', N'504669601', N'890120504669601', N'SEK', SYSDATETIME(), N'seed'),
    (@CPtaId3B, N'83279', N'503797511', N'832790503797511', N'SEK', SYSDATETIME(), N'seed');

INSERT INTO CAMT.ReportConfig
(ConfigId, ReportType, ReportVersion, ReportFrequency, Description, MessageRecipientId, AccountFormat, IsActive, IsPaginated, IsEmptyReportAllowed, IsBundled, CreatedAt, CreatedBy)
VALUES (10000203, N'CAMT054D', N'V02', N'DAILY', N'Scenario 3a - Daily debit notification', @CRecipientId3, N'IBAN', 1, 0, 1, 0, SYSDATETIME(), N'seed');
SET @CConfigId3A = SCOPE_IDENTITY();

INSERT INTO CAMT.ReportConfig
(ConfigId, ReportType, ReportVersion, ReportFrequency, Description, MessageRecipientId, AccountFormat, IsActive, IsPaginated, IsEmptyReportAllowed, IsBundled, CreatedAt, CreatedBy)
VALUES (10000204, N'CAMT052B', N'V02', N'EVERY_2_HOURS', N'Scenario 3b - Balances every 2 hours', @CRecipientId3, N'IBAN', 1, 0, 1, 0, SYSDATETIME(), N'seed');
SET @CConfigId3B = SCOPE_IDENTITY();

INSERT INTO CAMT.ReportAgreementScope (ReportConfigId, AgreementScopeId, CreatedAt, CreatedBy)
VALUES
    (@CConfigId3A, @CScopeId3A, SYSDATETIME(), N'seed'),
    (@CConfigId3B, @CScopeId3B, SYSDATETIME(), N'seed');

    PRINT '  ✓ Scenario 3 seeded.';
    PRINT '';

    -- -------------------------------------------------------------------
    -- C.4 Scenario 4: dual scope - CAMT052BT EVERY_4_HOURS + CAMT053S DAILY (bundled)
    -- -------------------------------------------------------------------
    PRINT '  - C.4: Scenario 4 - dual scope (Every-4-Hours + Daily Bundled)...';

INSERT INTO CAMT.Recipient (Type, Value, Name, CreatedAt, CreatedBy)
VALUES (N'SIGNER_ID', N'TEST-SCN-04', N'Test Recipient - Scenario 4', SYSDATETIME(), N'seed');
SET @CRecipientId4 = SCOPE_IDENTITY();

INSERT INTO CAMT.Agreement
(Id, Name, EngagementBank, EngagementId, Channel, Track, StartDate, CreatedAt, CreatedBy)
VALUES (N'AGR-TEST-SCN-04', N'Test Agmt - Snapshot+Hourly Dual', N'8999', N'061111111114', N'Customer Portal', N'STANDARD', SYSDATETIME(), SYSDATETIME(), N'seed');

INSERT INTO CAMT.AgreementVersion
(VersionId, AgreementId, Status, PricingOrderRef, CreatedAt, ActivatedAt, CreatedBy, Version)
VALUES (CAST(NEWID() AS NVARCHAR(50)), N'AGR-TEST-SCN-04', N'ACTIVE', N'P2B-061111111114-01', SYSDATETIME(), SYSDATETIME(), N'seed', 0);
SET @CVersionId4 = SCOPE_IDENTITY();

INSERT INTO CAMT.AgreementScope
(AgreementVersionId, Name, MessageRecipientId, ReportType, Status, CreatedAt, ActivatedAt, CreatedBy)
VALUES (@CVersionId4, N'CAMT052BT - V02 - EVERY_4_HOURS', @CRecipientId4, N'CAMT052BT', N'ACTIVE', SYSDATETIME(), SYSDATETIME(), N'seed');
SET @CScopeId4A = SCOPE_IDENTITY();

INSERT INTO CAMT.AgreementScope
(AgreementVersionId, Name, MessageRecipientId, ReportType, Status, CreatedAt, ActivatedAt, CreatedBy)
VALUES (@CVersionId4, N'CAMT053S - V02 - DAILY', @CRecipientId4, N'CAMT053S', N'ACTIVE', SYSDATETIME(), SYSDATETIME(), N'seed');
SET @CScopeId4B = SCOPE_IDENTITY();

INSERT INTO CAMT.PaymentTypeAssignment (AgreementScopeId, PaymentType, CreatedAt, CreatedBy)
VALUES (@CScopeId4A, N'ALL', SYSDATETIME(), N'seed');
SET @CPtaId4A = SCOPE_IDENTITY();

INSERT INTO CAMT.PaymentTypeAssignment (AgreementScopeId, PaymentType, CreatedAt, CreatedBy)
VALUES (@CScopeId4B, N'ALL', SYSDATETIME(), N'seed');
SET @CPtaId4B = SCOPE_IDENTITY();

INSERT INTO CAMT.AccountAssignment
(PaymentTypeAssignmentId, ClearingNumber, AccountNumber, AccountBBAN, Currency, CreatedAt, CreatedBy)
VALUES
    (@CPtaId4A, N'89012', N'504669691', N'890120504669691', N'SEK', SYSDATETIME(), N'seed'),
    (@CPtaId4A, N'83279', N'503797519', N'832790503797519', N'SEK', SYSDATETIME(), N'seed'),
    (@CPtaId4B, N'89013', N'504669602', N'890130504669602', N'SEK', SYSDATETIME(), N'seed'),
    (@CPtaId4B, N'83279', N'503797512', N'832790503797512', N'SEK', SYSDATETIME(), N'seed');

INSERT INTO CAMT.ReportConfig
(ConfigId, ReportType, ReportVersion, ReportFrequency, Description, MessageRecipientId, AccountFormat, IsActive, IsPaginated, IsEmptyReportAllowed, IsBundled, CreatedAt, CreatedBy)
VALUES (10000205, N'CAMT052BT', N'V02', N'EVERY_4_HOURS', N'Scenario 4a - Balances+transactions every 4 hours', @CRecipientId4, N'IBAN', 1, 0, 1, 0, SYSDATETIME(), N'seed');
SET @CConfigId4A = SCOPE_IDENTITY();

INSERT INTO CAMT.ReportConfig
(ConfigId, ReportType, ReportVersion, ReportFrequency, Description, MessageRecipientId, AccountFormat, IsActive, IsPaginated, IsEmptyReportAllowed, IsBundled, CreatedAt, CreatedBy)
VALUES (10000206, N'CAMT053S', N'V02', N'DAILY', N'Scenario 4b - Daily standard, bundled', @CRecipientId4, N'IBAN', 1, 0, 1, 1, SYSDATETIME(), N'seed');
SET @CConfigId4B = SCOPE_IDENTITY();

INSERT INTO CAMT.ReportAgreementScope (ReportConfigId, AgreementScopeId, CreatedAt, CreatedBy)
VALUES
    (@CConfigId4A, @CScopeId4A, SYSDATETIME(), N'seed'),
    (@CConfigId4B, @CScopeId4B, SYSDATETIME(), N'seed');

    PRINT '  ✓ Scenario 4 seeded.';
    PRINT '';

    -- -------------------------------------------------------------------
    -- C.5 Scenario 5: dual scope - CAMT052BT EVERY_1_HOUR + CAMT052B EVERY_4_HOURS (bundled)
    -- -------------------------------------------------------------------
    PRINT '  - C.5: Scenario 5 - dual scope (Hourly + Every-4-Hours Bundled)...';

INSERT INTO CAMT.Recipient (Type, Value, Name, CreatedAt, CreatedBy)
VALUES (N'SIGNER_ID', N'TEST-SCN-05', N'Test Recipient - Scenario 5', SYSDATETIME(), N'seed');
SET @CRecipientId5 = SCOPE_IDENTITY();

INSERT INTO CAMT.Agreement
(Id, Name, EngagementBank, EngagementId, Channel, Track, StartDate, CreatedAt, CreatedBy)
VALUES (N'AGR-TEST-SCN-05', N'Test Agmt - Snapshot Bundled Pair', N'8999', N'061111111115', N'Customer Portal', N'STANDARD', SYSDATETIME(), SYSDATETIME(), N'seed');

INSERT INTO CAMT.AgreementVersion
(VersionId, AgreementId, Status, PricingOrderRef, CreatedAt, ActivatedAt, CreatedBy, Version)
VALUES (CAST(NEWID() AS NVARCHAR(50)), N'AGR-TEST-SCN-05', N'ACTIVE', N'P2B-061111111115-01', SYSDATETIME(), SYSDATETIME(), N'seed', 0);
SET @CVersionId5 = SCOPE_IDENTITY();

INSERT INTO CAMT.AgreementScope
(AgreementVersionId, Name, MessageRecipientId, ReportType, Status, CreatedAt, ActivatedAt, CreatedBy)
VALUES (@CVersionId5, N'CAMT052BT - V02 - EVERY_1_HOUR', @CRecipientId5, N'CAMT052BT', N'ACTIVE', SYSDATETIME(), SYSDATETIME(), N'seed');
SET @CScopeId5A = SCOPE_IDENTITY();

INSERT INTO CAMT.AgreementScope
(AgreementVersionId, Name, MessageRecipientId, ReportType, Status, CreatedAt, ActivatedAt, CreatedBy)
VALUES (@CVersionId5, N'CAMT052B - V02 - EVERY_4_HOURS', @CRecipientId5, N'CAMT052B', N'ACTIVE', SYSDATETIME(), SYSDATETIME(), N'seed');
SET @CScopeId5B = SCOPE_IDENTITY();

INSERT INTO CAMT.PaymentTypeAssignment (AgreementScopeId, PaymentType, CreatedAt, CreatedBy)
VALUES (@CScopeId5A, N'ALL', SYSDATETIME(), N'seed');
SET @CPtaId5A = SCOPE_IDENTITY();

INSERT INTO CAMT.PaymentTypeAssignment (AgreementScopeId, PaymentType, CreatedAt, CreatedBy)
VALUES (@CScopeId5B, N'ALL', SYSDATETIME(), N'seed');
SET @CPtaId5B = SCOPE_IDENTITY();

INSERT INTO CAMT.AccountAssignment
(PaymentTypeAssignmentId, ClearingNumber, AccountNumber, AccountBBAN, Currency, CreatedAt, CreatedBy)
VALUES
    (@CPtaId5A, N'89012', N'504669691', N'890120504669691', N'SEK', SYSDATETIME(), N'seed'),
    (@CPtaId5A, N'83279', N'503797519', N'832790503797519', N'SEK', SYSDATETIME(), N'seed'),
    (@CPtaId5B, N'89012', N'504669601', N'890120504669601', N'SEK', SYSDATETIME(), N'seed'),
    (@CPtaId5B, N'83279', N'503797511', N'832790503797511', N'SEK', SYSDATETIME(), N'seed');

INSERT INTO CAMT.ReportConfig
(ConfigId, ReportType, ReportVersion, ReportFrequency, Description, MessageRecipientId, AccountFormat, IsActive, IsPaginated, IsEmptyReportAllowed, IsBundled, CreatedAt, CreatedBy)
VALUES (10000207, N'CAMT052BT', N'V02', N'EVERY_1_HOUR', N'Scenario 5a - Balances+transactions hourly', @CRecipientId5, N'IBAN', 1, 0, 1, 0, SYSDATETIME(), N'seed');
SET @CConfigId5A = SCOPE_IDENTITY();

INSERT INTO CAMT.ReportConfig
(ConfigId, ReportType, ReportVersion, ReportFrequency, Description, MessageRecipientId, AccountFormat, IsActive, IsPaginated, IsEmptyReportAllowed, IsBundled, CreatedAt, CreatedBy)
VALUES (10000208, N'CAMT052B', N'V02', N'EVERY_4_HOURS', N'Scenario 5b - Balances every 4 hours, bundled', @CRecipientId5, N'IBAN', 1, 0, 1, 1, SYSDATETIME(), N'seed');
SET @CConfigId5B = SCOPE_IDENTITY();

INSERT INTO CAMT.ReportAgreementScope (ReportConfigId, AgreementScopeId, CreatedAt, CreatedBy)
VALUES
    (@CConfigId5A, @CScopeId5A, SYSDATETIME(), N'seed'),
    (@CConfigId5B, @CScopeId5B, SYSDATETIME(), N'seed');

    PRINT '  ✓ Scenario 5 seeded.';
    PRINT '';
    PRINT '✓ Section C complete.';
    PRINT '';

    -- =========================================================================
    -- SECTION D: MULTI-PAYMENT-TYPE SCOPE & REMAINING FREQUENCY COVERAGE
    --   D.1 - a single AgreementScope with TWO PaymentTypeAssignment rows
    --         (INSTDOM + INCALIAS), each with its own account -
    --         nothing before this exercised a scope fanning out to more
    --         than one payment type. Also covers EVERY_4_HOURS.
    --   D.2/D.3/D.4 - simple single-scope agreements covering the three
    --         remaining ReportFrequency codes no seed data used yet:
    --         EVERY_30_MIN, EVERY_2_HOURS, EIGHT_TIMES_PER_DAY.
    -- =========================================================================

    PRINT '>>> Section D: Multi-payment-type scope & remaining frequency coverage';
    PRINT '';

    -- -------------------------------------------------------------------
    -- D.1 Multi-PaymentType scope: INSTDOM + INCALIAS under
    --     one AgreementScope, each with its own funded account.
    -- -------------------------------------------------------------------
    PRINT '  - D.1: Multi-payment-type scope (INSTDOM + INCALIAS)...';

INSERT INTO CAMT.Recipient (Type, Value, Name, CreatedAt, CreatedBy)
VALUES (N'SIGNER_ID', N'TEST-MULTI-PTA-01', N'Test Recipient - Multi-PaymentType Scope', SYSDATETIME(), N'seed');
SET @DRecipientId1 = SCOPE_IDENTITY();

INSERT INTO CAMT.Agreement
(Id, Name, EngagementBank, EngagementId, Channel, Track, StartDate, CreatedAt, CreatedBy)
VALUES (N'AGR-TEST-MULTI-PTA-01', N'Test Agmt - Multi-PaymentType', N'8999', N'061111111116', N'Customer Portal', N'STANDARD', SYSDATETIME(), SYSDATETIME(), N'seed');

INSERT INTO CAMT.AgreementVersion
(VersionId, AgreementId, Status, PricingOrderRef, CreatedAt, ActivatedAt, CreatedBy, Version)
VALUES (CAST(NEWID() AS NVARCHAR(50)), N'AGR-TEST-MULTI-PTA-01', N'ACTIVE', N'P2B-061111111116-01', SYSDATETIME(), SYSDATETIME(), N'seed', 0);
SET @DVersionId1 = SCOPE_IDENTITY();

INSERT INTO CAMT.AgreementScope
(AgreementVersionId, Name, MessageRecipientId, ReportType, Status, CreatedAt, ActivatedAt, CreatedBy)
VALUES (@DVersionId1, N'CAMT052BT - V02 - EVERY_4_HOURS (Multi-PaymentType)', @DRecipientId1, N'CAMT052BT', N'ACTIVE', SYSDATETIME(), SYSDATETIME(), N'seed');
SET @DScopeId1 = SCOPE_IDENTITY();

INSERT INTO CAMT.PaymentTypeAssignment (AgreementScopeId, PaymentType, CreatedAt, CreatedBy)
VALUES (@DScopeId1, N'INSTDOM', SYSDATETIME(), N'seed');
SET @DPtaId1CT = SCOPE_IDENTITY();

INSERT INTO CAMT.PaymentTypeAssignment (AgreementScopeId, PaymentType, CreatedAt, CreatedBy)
VALUES (@DScopeId1, N'INCALIAS', SYSDATETIME(), N'seed');
SET @DPtaId1DD = SCOPE_IDENTITY();

INSERT INTO CAMT.AccountAssignment
(PaymentTypeAssignmentId, ClearingNumber, AccountNumber, AccountBBAN, Currency, CreatedAt, CreatedBy)
VALUES
    (@DPtaId1CT, N'89014', N'504669610', N'890140504669610', N'SEK', SYSDATETIME(), N'seed'),
    (@DPtaId1DD, N'89015', N'504669611', N'890150504669611', N'SEK', SYSDATETIME(), N'seed');

INSERT INTO CAMT.ReportConfig
(ConfigId, ReportType, ReportVersion, ReportFrequency, Description, MessageRecipientId, AccountFormat, IsActive, IsPaginated, IsEmptyReportAllowed, IsBundled, CreatedAt, CreatedBy)
VALUES (10000209, N'CAMT052BT', N'V02', N'EVERY_4_HOURS', N'Multi-payment-type scope (INSTDOM + INCALIAS), bundled', @DRecipientId1, N'IBAN', 1, 0, 1, 1, SYSDATETIME(), N'seed');
SET @DConfigId1 = SCOPE_IDENTITY();

INSERT INTO CAMT.ReportAgreementScope (ReportConfigId, AgreementScopeId, CreatedAt, CreatedBy)
VALUES (@DConfigId1, @DScopeId1, SYSDATETIME(), N'seed');

    PRINT '  ✓ D.1 seeded: scope ' + CAST(@DScopeId1 AS VARCHAR) + ' has 2 PaymentTypeAssignment rows.';
    PRINT '';

    -- -------------------------------------------------------------------
    -- D.2 EVERY_30_MIN frequency coverage
    -- -------------------------------------------------------------------
    PRINT '  - D.2: EVERY_30_MIN frequency coverage...';

INSERT INTO CAMT.Recipient (Type, Value, Name, CreatedAt, CreatedBy)
VALUES (N'SIGNER_ID', N'TEST-FREQ-30MIN', N'Test Recipient - EVERY_30_MIN Coverage', SYSDATETIME(), N'seed');
SET @DRecipientId2 = SCOPE_IDENTITY();

INSERT INTO CAMT.Agreement
(Id, Name, EngagementBank, EngagementId, Channel, Track, StartDate, CreatedAt, CreatedBy)
VALUES (N'AGR-TEST-FREQ-30MIN', N'Test Agmt - EVERY_30_MIN Cov', N'8999', N'061111111117', N'Customer Portal', N'STANDARD', SYSDATETIME(), SYSDATETIME(), N'seed');

INSERT INTO CAMT.AgreementVersion
(VersionId, AgreementId, Status, PricingOrderRef, CreatedAt, ActivatedAt, CreatedBy, Version)
VALUES (CAST(NEWID() AS NVARCHAR(50)), N'AGR-TEST-FREQ-30MIN', N'ACTIVE', N'P2B-061111111117-01', SYSDATETIME(), SYSDATETIME(), N'seed', 0);
SET @DVersionId2 = SCOPE_IDENTITY();

INSERT INTO CAMT.AgreementScope
(AgreementVersionId, Name, MessageRecipientId, ReportType, Status, CreatedAt, ActivatedAt, CreatedBy)
VALUES (@DVersionId2, N'CAMT052B - V02 - EVERY_30_MIN', @DRecipientId2, N'CAMT052B', N'ACTIVE', SYSDATETIME(), SYSDATETIME(), N'seed');
SET @DScopeId2 = SCOPE_IDENTITY();

INSERT INTO CAMT.PaymentTypeAssignment (AgreementScopeId, PaymentType, CreatedAt, CreatedBy)
VALUES (@DScopeId2, N'ALL', SYSDATETIME(), N'seed');
SET @DPtaId2 = SCOPE_IDENTITY();

INSERT INTO CAMT.AccountAssignment
(PaymentTypeAssignmentId, ClearingNumber, AccountNumber, AccountBBAN, Currency, CreatedAt, CreatedBy)
VALUES (@DPtaId2, N'89020', N'504669620', N'890200504669620', N'SEK', SYSDATETIME(), N'seed');

INSERT INTO CAMT.ReportConfig
(ConfigId, ReportType, ReportVersion, ReportFrequency, Description, MessageRecipientId, AccountFormat, IsActive, IsPaginated, IsEmptyReportAllowed, IsBundled, CreatedAt, CreatedBy)
VALUES (10000210, N'CAMT052B', N'V02', N'EVERY_30_MIN', N'EVERY_30_MIN frequency coverage', @DRecipientId2, N'IBAN', 1, 0, 1, 0, SYSDATETIME(), N'seed');
SET @DConfigId2 = SCOPE_IDENTITY();

INSERT INTO CAMT.ReportAgreementScope (ReportConfigId, AgreementScopeId, CreatedAt, CreatedBy)
VALUES (@DConfigId2, @DScopeId2, SYSDATETIME(), N'seed');

    PRINT '  ✓ D.2 seeded.';
    PRINT '';

    -- -------------------------------------------------------------------
    -- D.3 EVERY_2_HOURS frequency coverage
    -- -------------------------------------------------------------------
    PRINT '  - D.3: EVERY_2_HOURS frequency coverage...';

INSERT INTO CAMT.Recipient (Type, Value, Name, CreatedAt, CreatedBy)
VALUES (N'SIGNER_ID', N'TEST-FREQ-2HR', N'Test Recipient - EVERY_2_HOURS Coverage', SYSDATETIME(), N'seed');
SET @DRecipientId3 = SCOPE_IDENTITY();

INSERT INTO CAMT.Agreement
(Id, Name, EngagementBank, EngagementId, Channel, Track, StartDate, CreatedAt, CreatedBy)
VALUES (N'AGR-TEST-FREQ-2HR', N'Test Agmt - EVERY_2_HOURS Cov', N'8999', N'061111111118', N'Customer Portal', N'STANDARD', SYSDATETIME(), SYSDATETIME(), N'seed');

INSERT INTO CAMT.AgreementVersion
(VersionId, AgreementId, Status, PricingOrderRef, CreatedAt, ActivatedAt, CreatedBy, Version)
VALUES (CAST(NEWID() AS NVARCHAR(50)), N'AGR-TEST-FREQ-2HR', N'ACTIVE', N'P2B-061111111118-01', SYSDATETIME(), SYSDATETIME(), N'seed', 0);
SET @DVersionId3 = SCOPE_IDENTITY();

INSERT INTO CAMT.AgreementScope
(AgreementVersionId, Name, MessageRecipientId, ReportType, Status, CreatedAt, ActivatedAt, CreatedBy)
VALUES (@DVersionId3, N'CAMT052BT - V02 - EVERY_2_HOURS', @DRecipientId3, N'CAMT052BT', N'ACTIVE', SYSDATETIME(), SYSDATETIME(), N'seed');
SET @DScopeId3 = SCOPE_IDENTITY();

INSERT INTO CAMT.PaymentTypeAssignment (AgreementScopeId, PaymentType, CreatedAt, CreatedBy)
VALUES (@DScopeId3, N'ALL', SYSDATETIME(), N'seed');
SET @DPtaId3 = SCOPE_IDENTITY();

INSERT INTO CAMT.AccountAssignment
(PaymentTypeAssignmentId, ClearingNumber, AccountNumber, AccountBBAN, Currency, CreatedAt, CreatedBy)
VALUES (@DPtaId3, N'89021', N'504669621', N'890210504669621', N'SEK', SYSDATETIME(), N'seed');

INSERT INTO CAMT.ReportConfig
(ConfigId, ReportType, ReportVersion, ReportFrequency, Description, MessageRecipientId, AccountFormat, IsActive, IsPaginated, IsEmptyReportAllowed, IsBundled, CreatedAt, CreatedBy)
VALUES (10000211, N'CAMT052BT', N'V02', N'EVERY_2_HOURS', N'EVERY_2_HOURS frequency coverage', @DRecipientId3, N'IBAN', 1, 0, 1, 0, SYSDATETIME(), N'seed');
SET @DConfigId3 = SCOPE_IDENTITY();

INSERT INTO CAMT.ReportAgreementScope (ReportConfigId, AgreementScopeId, CreatedAt, CreatedBy)
VALUES (@DConfigId3, @DScopeId3, SYSDATETIME(), N'seed');

    PRINT '  ✓ D.3 seeded.';
    PRINT '';

    -- -------------------------------------------------------------------
    -- D.4 EIGHT_TIMES_PER_DAY frequency coverage
    -- -------------------------------------------------------------------
    PRINT '  - D.4: EIGHT_TIMES_PER_DAY frequency coverage...';

INSERT INTO CAMT.Recipient (Type, Value, Name, CreatedAt, CreatedBy)
VALUES (N'SIGNER_ID', N'TEST-FREQ-8XDAY', N'Test Recipient - EIGHT_TIMES_PER_DAY Coverage', SYSDATETIME(), N'seed');
SET @DRecipientId4 = SCOPE_IDENTITY();

INSERT INTO CAMT.Agreement
(Id, Name, EngagementBank, EngagementId, Channel, Track, StartDate, CreatedAt, CreatedBy)
VALUES (N'AGR-TEST-FREQ-8XDAY', N'Test Agmt - 8x/Day Coverage', N'8999', N'061111111119', N'Customer Portal', N'NOTIFICATION', SYSDATETIME(), SYSDATETIME(), N'seed');

INSERT INTO CAMT.AgreementVersion
(VersionId, AgreementId, Status, PricingOrderRef, CreatedAt, ActivatedAt, CreatedBy, Version)
VALUES (CAST(NEWID() AS NVARCHAR(50)), N'AGR-TEST-FREQ-8XDAY', N'ACTIVE', N'P2B-061111111119-01', SYSDATETIME(), SYSDATETIME(), N'seed', 0);
SET @DVersionId4 = SCOPE_IDENTITY();

INSERT INTO CAMT.AgreementScope
(AgreementVersionId, Name, MessageRecipientId, ReportType, Status, CreatedAt, ActivatedAt, CreatedBy)
VALUES (@DVersionId4, N'CAMT054C - V02 - EIGHT_TIMES', @DRecipientId4, N'CAMT054C', N'ACTIVE', SYSDATETIME(), SYSDATETIME(), N'seed');
SET @DScopeId4 = SCOPE_IDENTITY();

INSERT INTO CAMT.PaymentTypeAssignment (AgreementScopeId, PaymentType, CreatedAt, CreatedBy)
VALUES (@DScopeId4, N'INSTDOM', SYSDATETIME(), N'seed');
SET @DPtaId4 = SCOPE_IDENTITY();

INSERT INTO CAMT.AccountAssignment
(PaymentTypeAssignmentId, ClearingNumber, AccountNumber, AccountBBAN, Currency, CreatedAt, CreatedBy)
VALUES (@DPtaId4, N'89022', N'504669622', N'890220504669622', N'SEK', SYSDATETIME(), N'seed');

INSERT INTO CAMT.ReportConfig
(ConfigId, ReportType, ReportVersion, ReportFrequency, Description, MessageRecipientId, AccountFormat, IsActive, IsPaginated, IsEmptyReportAllowed, IsBundled, CreatedAt, CreatedBy)
VALUES (10000212, N'CAMT054C', N'V02', N'EIGHT_TIMES_PER_DAY', N'EIGHT_TIMES_PER_DAY frequency coverage', @DRecipientId4, N'IBAN', 1, 0, 1, 0, SYSDATETIME(), N'seed');
SET @DConfigId4 = SCOPE_IDENTITY();

INSERT INTO CAMT.ReportAgreementScope (ReportConfigId, AgreementScopeId, CreatedAt, CreatedBy)
VALUES (@DConfigId4, @DScopeId4, SYSDATETIME(), N'seed');

    PRINT '  ✓ D.4 seeded.';
    PRINT '';
    PRINT '✓ Section D complete.';
    PRINT '';

    -- =========================================================================
    -- SECTION E: NON-ACTIVE LIFECYCLE STATES
    --   E.1 - Version history: a REPLACED (superseded) AgreementVersion
    --         alongside the current ACTIVE one on the same Agreement.
    --   E.2 - Pending activation: an Agreement whose only AgreementVersion
    --         is PENDING_ACTIVATION (no ACTIVE version exists yet), with a
    --         PENDING scope and an inactive ReportConfig (ConfigId NULL,
    --         exercising CK_ReportConfig_ActiveHasConfigId's NULL-when-
    --         inactive branch).
    --   E.3 - Cancelled scope: an ACTIVE version with one ACTIVE scope and
    --         one CANCELLED scope side by side; the cancelled scope's
    --         ReportConfig is likewise inactive with ConfigId NULL.
    -- =========================================================================

    PRINT '>>> Section E: Non-active lifecycle states';
    PRINT '';

    -- -------------------------------------------------------------------
    -- E.1 Version history: REPLACED -> ACTIVE
    -- -------------------------------------------------------------------
    PRINT '  - E.1: Version history (REPLACED then ACTIVE)...';

INSERT INTO CAMT.Recipient (Type, Value, Name, CreatedAt, CreatedBy)
VALUES (N'SIGNER_ID', N'TEST-VERHIST-01', N'Test Recipient - Version History', SYSDATETIME(), N'seed');
SET @ERecipientId1 = SCOPE_IDENTITY();

INSERT INTO CAMT.Agreement
(Id, Name, EngagementBank, EngagementId, Channel, Track, StartDate, CreatedAt, CreatedBy)
VALUES (N'AGR-TEST-VERHIST-01', N'Test Agreement - Version History', N'8999', N'061111111120', N'Customer Portal', N'STANDARD', SYSDATETIME(), SYSDATETIME(), N'seed');

INSERT INTO CAMT.AgreementVersion
(VersionId, AgreementId, Status, PricingOrderRef, CreatedAt, ActivatedAt, SupersededAt, CreatedBy, Version)
VALUES (CAST(NEWID() AS NVARCHAR(50)), N'AGR-TEST-VERHIST-01', N'REPLACED', N'P2B-061111111120-01', DATEADD(DAY, -30, SYSDATETIME()), DATEADD(DAY, -30, SYSDATETIME()), SYSDATETIME(), N'seed', 0);
SET @EVersionId1Old = SCOPE_IDENTITY();

INSERT INTO CAMT.AgreementVersion
(VersionId, AgreementId, Status, PricingOrderRef, CreatedAt, ActivatedAt, CreatedBy, Version)
VALUES (CAST(NEWID() AS NVARCHAR(50)), N'AGR-TEST-VERHIST-01', N'ACTIVE', N'P2B-061111111120-02', SYSDATETIME(), SYSDATETIME(), N'seed', 1);
SET @EVersionId1New = SCOPE_IDENTITY();

INSERT INTO CAMT.AgreementScope
(AgreementVersionId, Name, MessageRecipientId, ReportType, Status, CreatedAt, ActivatedAt, CreatedBy)
VALUES (@EVersionId1New, N'CAMT052B - V02 - EVERY_30_MIN (Version History)', @ERecipientId1, N'CAMT052B', N'ACTIVE', SYSDATETIME(), SYSDATETIME(), N'seed');
SET @EScopeId1 = SCOPE_IDENTITY();

INSERT INTO CAMT.PaymentTypeAssignment (AgreementScopeId, PaymentType, CreatedAt, CreatedBy)
VALUES (@EScopeId1, N'ALL', SYSDATETIME(), N'seed');
SET @EPtaId1 = SCOPE_IDENTITY();

INSERT INTO CAMT.AccountAssignment
(PaymentTypeAssignmentId, ClearingNumber, AccountNumber, AccountBBAN, Currency, CreatedAt, CreatedBy)
VALUES (@EPtaId1, N'89023', N'504669623', N'890230504669623', N'SEK', SYSDATETIME(), N'seed');

INSERT INTO CAMT.ReportConfig
(ConfigId, ReportType, ReportVersion, ReportFrequency, Description, MessageRecipientId, AccountFormat, IsActive, IsPaginated, IsEmptyReportAllowed, IsBundled, CreatedAt, CreatedBy)
VALUES (10000213, N'CAMT052B', N'V02', N'EVERY_30_MIN', N'Version-history agreement - current active scope', @ERecipientId1, N'IBAN', 1, 0, 1, 0, SYSDATETIME(), N'seed');
SET @EConfigId1 = SCOPE_IDENTITY();

INSERT INTO CAMT.ReportAgreementScope (ReportConfigId, AgreementScopeId, CreatedAt, CreatedBy)
VALUES (@EConfigId1, @EScopeId1, SYSDATETIME(), N'seed');

    PRINT '  ✓ E.1 seeded: 1 REPLACED version + 1 ACTIVE version on the same Agreement.';
    PRINT '';

    -- -------------------------------------------------------------------
    -- E.2 Pending activation: no ACTIVE version exists yet
    -- -------------------------------------------------------------------
    PRINT '  - E.2: Pending activation (no ACTIVE version yet)...';

INSERT INTO CAMT.Recipient (Type, Value, Name, CreatedAt, CreatedBy)
VALUES (N'SIGNER_ID', N'TEST-PENDING-01', N'Test Recipient - Pending Activation', SYSDATETIME(), N'seed');
SET @ERecipientId2 = SCOPE_IDENTITY();

INSERT INTO CAMT.Agreement
(Id, Name, EngagementBank, EngagementId, Channel, Track, StartDate, CreatedAt, CreatedBy)
VALUES (N'AGR-TEST-PENDING-01', N'Test Agreement - Pending Activation', N'8999', N'061111111121', N'Customer Portal', N'STANDARD', NULL, SYSDATETIME(), N'seed');

INSERT INTO CAMT.AgreementVersion
(VersionId, AgreementId, Status, PricingOrderRef, CreatedAt, CreatedBy, Version)
VALUES (CAST(NEWID() AS NVARCHAR(50)), N'AGR-TEST-PENDING-01', N'PENDING_ACTIVATION', N'P2B-061111111121-01', SYSDATETIME(), N'seed', 0);
SET @EVersionId2 = SCOPE_IDENTITY();

INSERT INTO CAMT.AgreementScope
(AgreementVersionId, Name, MessageRecipientId, ReportType, Status, CreatedAt, CreatedBy)
VALUES (@EVersionId2, N'CAMT054D - V02 - DAILY (Pending Activation)', @ERecipientId2, N'CAMT054D', N'PENDING', SYSDATETIME(), N'seed');
SET @EScopeId2 = SCOPE_IDENTITY();

INSERT INTO CAMT.PaymentTypeAssignment (AgreementScopeId, PaymentType, CreatedAt, CreatedBy)
VALUES (@EScopeId2, N'ALL', SYSDATETIME(), N'seed');
SET @EPtaId2 = SCOPE_IDENTITY();

INSERT INTO CAMT.AccountAssignment
(PaymentTypeAssignmentId, ClearingNumber, AccountNumber, AccountBBAN, Currency, CreatedAt, CreatedBy)
VALUES (@EPtaId2, N'89024', N'504669624', N'890240504669624', N'SEK', SYSDATETIME(), N'seed');

    -- IsActive = 0 -> ConfigId may be NULL (CK_ReportConfig_ActiveHasConfigId)
INSERT INTO CAMT.ReportConfig
(ConfigId, ReportType, ReportVersion, ReportFrequency, Description, MessageRecipientId, AccountFormat, IsActive, IsPaginated, IsEmptyReportAllowed, IsBundled, CreatedAt, CreatedBy)
VALUES (NULL, N'CAMT054D', N'V02', N'DAILY', N'Pending-activation agreement - not yet active, no ConfigId assigned', @ERecipientId2, N'IBAN', 0, 0, 1, 0, SYSDATETIME(), N'seed');
SET @EConfigId2 = SCOPE_IDENTITY();

INSERT INTO CAMT.ReportAgreementScope (ReportConfigId, AgreementScopeId, CreatedAt, CreatedBy)
VALUES (@EConfigId2, @EScopeId2, SYSDATETIME(), N'seed');

    PRINT '  ✓ E.2 seeded: PENDING_ACTIVATION version, PENDING scope, inactive ReportConfig with NULL ConfigId.';
    PRINT '';

    -- -------------------------------------------------------------------
    -- E.3 Cancelled scope alongside an active one
    -- -------------------------------------------------------------------
    PRINT '  - E.3: Cancelled scope alongside an active scope...';

INSERT INTO CAMT.Recipient (Type, Value, Name, CreatedAt, CreatedBy)
VALUES (N'SIGNER_ID', N'TEST-CANCELLED-01', N'Test Recipient - Cancelled Scope', SYSDATETIME(), N'seed');
SET @ERecipientId3 = SCOPE_IDENTITY();

INSERT INTO CAMT.Agreement
(Id, Name, EngagementBank, EngagementId, Channel, Track, StartDate, CreatedAt, CreatedBy)
VALUES (N'AGR-TEST-CANCELLED-01', N'Test Agreement - Cancelled Scope', N'8999', N'061111111122', N'Customer Portal', N'STANDARD', SYSDATETIME(), SYSDATETIME(), N'seed');

INSERT INTO CAMT.AgreementVersion
(VersionId, AgreementId, Status, PricingOrderRef, CreatedAt, ActivatedAt, CreatedBy, Version)
VALUES (CAST(NEWID() AS NVARCHAR(50)), N'AGR-TEST-CANCELLED-01', N'ACTIVE', N'P2B-061111111122-01', SYSDATETIME(), SYSDATETIME(), N'seed', 0);
SET @EVersionId3 = SCOPE_IDENTITY();

INSERT INTO CAMT.AgreementScope
(AgreementVersionId, Name, MessageRecipientId, ReportType, Status, CreatedAt, ActivatedAt, CreatedBy)
VALUES (@EVersionId3, N'CAMT052BT - V02 - EVERY_1_HOUR (Still Active)', @ERecipientId3, N'CAMT052BT', N'ACTIVE', SYSDATETIME(), SYSDATETIME(), N'seed');
SET @EScopeId3Active = SCOPE_IDENTITY();

INSERT INTO CAMT.AgreementScope
(AgreementVersionId, Name, MessageRecipientId, ReportType, Status, CreatedAt, ActivatedAt, CancelledAt, CreatedBy)
VALUES (@EVersionId3, N'CAMT052B - V02 - EVERY_30_MIN (Cancelled)', @ERecipientId3, N'CAMT052B', N'CANCELLED', DATEADD(DAY, -10, SYSDATETIME()), DATEADD(DAY, -10, SYSDATETIME()), SYSDATETIME(), N'seed');
SET @EScopeId3Cancelled = SCOPE_IDENTITY();

INSERT INTO CAMT.PaymentTypeAssignment (AgreementScopeId, PaymentType, CreatedAt, CreatedBy)
VALUES (@EScopeId3Active, N'ALL', SYSDATETIME(), N'seed');
SET @EPtaId3Active = SCOPE_IDENTITY();

INSERT INTO CAMT.PaymentTypeAssignment (AgreementScopeId, PaymentType, CreatedAt, CreatedBy)
VALUES (@EScopeId3Cancelled, N'ALL', SYSDATETIME(), N'seed');
SET @EPtaId3Cancelled = SCOPE_IDENTITY();

INSERT INTO CAMT.AccountAssignment
(PaymentTypeAssignmentId, ClearingNumber, AccountNumber, AccountBBAN, Currency, CreatedAt, CreatedBy)
VALUES
    (@EPtaId3Active, N'89025', N'504669625', N'890250504669625', N'SEK', SYSDATETIME(), N'seed'),
    (@EPtaId3Cancelled, N'89026', N'504669626', N'890260504669626', N'SEK', SYSDATETIME(), N'seed');

INSERT INTO CAMT.ReportConfig
(ConfigId, ReportType, ReportVersion, ReportFrequency, Description, MessageRecipientId, AccountFormat, IsActive, IsPaginated, IsEmptyReportAllowed, IsBundled, CreatedAt, CreatedBy)
VALUES (10000214, N'CAMT052BT', N'V02', N'EVERY_1_HOUR', N'Cancelled-scope agreement - still-active scope''s config', @ERecipientId3, N'IBAN', 1, 0, 1, 0, SYSDATETIME(), N'seed');
SET @EConfigId3Active = SCOPE_IDENTITY();

    -- IsActive = 0 -> ConfigId may be NULL - the cancelled scope's config is retired
INSERT INTO CAMT.ReportConfig
(ConfigId, ReportType, ReportVersion, ReportFrequency, Description, MessageRecipientId, AccountFormat, IsActive, IsPaginated, IsEmptyReportAllowed, IsBundled, CreatedAt, CreatedBy)
VALUES (NULL, N'CAMT052B', N'V02', N'EVERY_30_MIN', N'Cancelled-scope agreement - cancelled scope''s config, retired', @ERecipientId3, N'IBAN', 0, 0, 1, 0, SYSDATETIME(), N'seed');
SET @EConfigId3Cancelled = SCOPE_IDENTITY();

INSERT INTO CAMT.ReportAgreementScope (ReportConfigId, AgreementScopeId, CreatedAt, CreatedBy)
VALUES
    (@EConfigId3Active, @EScopeId3Active, SYSDATETIME(), N'seed'),
    (@EConfigId3Cancelled, @EScopeId3Cancelled, SYSDATETIME(), N'seed');

    PRINT '  ✓ E.3 seeded: 1 ACTIVE scope + 1 CANCELLED scope on the same version, cancelled scope''s config retired (NULL ConfigId).';
    PRINT '';
    PRINT '✓ Section E complete.';
    PRINT '';

    -- =========================================================================
    -- VALIDATION
    -- =========================================================================

    PRINT '>>> Validation: Verifying row counts...';

SELECT 'Recipient' AS TableName, COUNT(*) AS [RowCount] FROM CAMT.Recipient
UNION ALL SELECT 'Agreement', COUNT(*) FROM CAMT.Agreement
UNION ALL SELECT 'AgreementContact', COUNT(*) FROM CAMT.AgreementContact
UNION ALL SELECT 'AgreementVersion', COUNT(*) FROM CAMT.AgreementVersion
UNION ALL SELECT 'AgreementScope', COUNT(*) FROM CAMT.AgreementScope
UNION ALL SELECT 'PaymentTypeAssignment', COUNT(*) FROM CAMT.PaymentTypeAssignment
UNION ALL SELECT 'AccountAssignment', COUNT(*) FROM CAMT.AccountAssignment
UNION ALL SELECT 'AliasAssignment', COUNT(*) FROM CAMT.AliasAssignment
UNION ALL SELECT 'ReportConfig', COUNT(*) FROM CAMT.ReportConfig
UNION ALL SELECT 'ReportAgreementScope', COUNT(*) FROM CAMT.ReportAgreementScope;

IF NOT EXISTS (
        SELECT 1 FROM CAMT.ReportConfig rc
        WHERE rc.Id = @EdgeConfigId1
          AND NOT EXISTS (SELECT 1 FROM CAMT.ReportAgreementScope ras WHERE ras.ReportConfigId = rc.Id)
    )
        RAISERROR('ERROR: expected zero-scope edge-case config to have no ReportAgreementScope rows!', 16, 1);

    IF (SELECT COUNT(*) FROM CAMT.ReportAgreementScope WHERE ReportConfigId = @EdgeConfigId3) <> 2
        RAISERROR('ERROR: expected multi-scope fan-in edge-case config to have exactly 2 ReportAgreementScope rows!', 16, 1);

    IF (SELECT COUNT(*) FROM CAMT.PaymentTypeAssignment WHERE AgreementScopeId = @DScopeId1) <> 2
        RAISERROR('ERROR: expected Section D.1 multi-payment-type scope to have exactly 2 PaymentTypeAssignment rows!', 16, 1);

    IF (SELECT COUNT(*) FROM CAMT.AgreementVersion WHERE AgreementId = N'AGR-TEST-VERHIST-01') <> 2
        RAISERROR('ERROR: expected Section E.1 version-history agreement to have exactly 2 AgreementVersion rows!', 16, 1);

    IF NOT EXISTS (SELECT 1 FROM CAMT.AgreementVersion WHERE Id = @EVersionId1Old AND Status = N'REPLACED')
        RAISERROR('ERROR: expected Section E.1 old version to be REPLACED!', 16, 1);

    IF EXISTS (SELECT 1 FROM CAMT.ReportConfig WHERE Id = @EConfigId2 AND (ConfigId IS NOT NULL OR IsActive = 1))
        RAISERROR('ERROR: expected Section E.2 pending-activation ReportConfig to have NULL ConfigId and IsActive = 0!', 16, 1);

    IF EXISTS (SELECT 1 FROM CAMT.ReportConfig WHERE Id = @EConfigId3Cancelled AND (ConfigId IS NOT NULL OR IsActive = 1))
        RAISERROR('ERROR: expected Section E.3 cancelled-scope ReportConfig to have NULL ConfigId and IsActive = 0!', 16, 1);

    IF NOT EXISTS (SELECT 1 FROM CAMT.AgreementScope WHERE Id = @EScopeId3Cancelled AND Status = N'CANCELLED' AND CancelledAt IS NOT NULL)
        RAISERROR('ERROR: expected Section E.3 cancelled scope to have Status = CANCELLED and CancelledAt set!', 16, 1);

PRINT '✓ Validation complete.';
    PRINT '';

COMMIT TRANSACTION;
PRINT '========================================';
    PRINT '✓ SCRIPT 08 COMPLETED SUCCESSFULLY';
    PRINT 'END TIME: ' + CAST(GETDATE() AS VARCHAR);
    PRINT '========================================';

END TRY
BEGIN CATCH
IF @@TRANCOUNT > 0
        ROLLBACK TRANSACTION;

    PRINT '';
    PRINT '========================================';
    PRINT '✗ SCRIPT 08 FAILED!';
    PRINT 'ERROR: ' + ERROR_MESSAGE();
    PRINT 'ERROR NUMBER: ' + CAST(ERROR_NUMBER() AS VARCHAR);
    PRINT 'ERROR LINE: ' + CAST(ERROR_LINE() AS VARCHAR);
    PRINT '========================================';

    THROW;
END CATCH
GO