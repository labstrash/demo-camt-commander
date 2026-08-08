# CAMT Schema - Deployment Scripts

## Overview
This set of SQL scripts performs a **clean deployment** of the CAMT (Cash Management) reporting schema for the Corporate Banking Reporting Agreement System — schema, reference/lookup data, Spring Batch job-repository tables, and the Quartz job store.

The same `.sql` files are used in **two environments**, which run a different subset of them:

| Environment | Runs | Entry point |
|---|---|---|
| **Local development** | Everything, `00` through `99`, including `08-seed-data.sql` | `infra/docker/compose.yaml`'s `sqlserver-init` service (automatic on `docker compose up`) |
| **Production** | `01`-`07`, `97`, `99` only. `00-drop-all.sql`/`96-drop-batch.sql`/`98-drop-quartz.sql` only if explicitly confirmed. **`08-seed-data.sql` is never run.** | `../deploy-production.sh` |

**`08-seed-data.sql` seeds fabricated demo/dev business data (Agreements, Recipients, ReportConfigs) — it must never be run against a real production database.** `01-schema-reference.sql`'s seed data (`ReportType`/`PaymentType`/`ReportFrequency`) is different: that's required reference data the application needs to function, identical in every environment.

## Prerequisites
- **SQL Server** (2016 or later recommended)
- **Database**: `REPORTDB` must already exist
- **Permissions**:
  - `CREATE SCHEMA` and `DROP SCHEMA` permissions in `REPORTDB`
  - `CREATE TABLE`, `CREATE PROCEDURE` permissions
  - `ALTER` permissions on the database
- **Backup**: Ensure you have a full backup of the `REPORTDB` database before running the drop scripts against any environment that holds real data

## ⚠️ WARNING
**`00-drop-all.sql`, `96-drop-batch.sql`, and `98-drop-quartz.sql` permanently delete existing objects** (the CAMT schema and its data, Spring Batch's `BATCH_*` tables, and Quartz's `QRTZ_*` tables, respectively).

**Do NOT run these against a database that holds real data unless you have a backup and intend to lose everything in scope.** `deploy-production.sh` gates all three behind an explicit `--confirm-drop` flag plus typing the target database name back, specifically so this can't happen by accident.

## Script Execution Order (local development — everything)
Execute the scripts in the following order:

| Order | Script File | Purpose |
|-------|-------------|---------|
| 1 | `00-drop-all.sql` | **CLEANUP** (local-only). Drops the entire CAMT schema and all its objects |
| 2 | `01-schema-reference.sql` | Creates CAMT schema, reference tables (ReportType, PaymentType, ReportFrequency) and loads seed data |
| 3 | `02-schema-sequence.sql` | Creates AgreementSequence table and GetNextAgreementSequence stored procedure |
| 4 | `03-schema-agreement.sql` | Creates core agreement tables: Recipient, Agreement, AgreementContact, AgreementVersion |
| 5 | `04-schema-scope.sql` | Creates scope and assignment tables: AgreementScope, PaymentTypeAssignment, AccountAssignment, AliasAssignment |
| 6 | `05-schema-report.sql` | Creates report configuration tables: ReportConfig, ReportAgreementScope |
| 7 | `06-schema-audit-deadletter.sql` | Creates DeadLetterMessage and ReportCommandAudit tables |
| 8 | `07-schema-fetch-config.sql` | Creates the `dbo.BigIntIdList` table type for id-set query parameters |
| 9 | `08-seed-data.sql` | **Local-only.** Seeds demo/dev data: happy-path scenarios, fetch-config read-path edge cases, realistic multi-scope onboarding scenarios, multi-payment-type/frequency coverage, and non-active lifecycle states (see Section breakdown below) |
| 10 | `96-drop-batch.sql` | **CLEANUP** (local-only). Drops all Spring Batch (`BATCH_*`) job repository objects |
| 11 | `97-schema-batch.sql` | Creates the Spring Batch job repository tables and sequences |
| 12 | `98-drop-quartz.sql` | **CLEANUP** (local-only). Drops all Quartz (`QRTZ_*`) job store objects |
| 13 | `99-schema-quartz.sql` | Creates the Quartz JDBC job store tables, foreign keys, and indexes |

`96`-`99` are numbered out of sequence deliberately: Spring Batch and Quartz are both unrelated to the CAMT schema, so keeping their drop/create pairs together at the end leaves room to add more CAMT-related scripts (08, 09, ...) without renumbering.

## How to Run

### Local development
Handled automatically by `docker compose -f infra/docker/compose.yaml up` — the `sqlserver-init` service runs every script above, in order, every time. Nothing to run manually.

### Production
Use `../deploy-production.sh` — see that script's own header comment for full usage. Summary:

```bash
DB_SERVER=myserver.example.com \
DB_USER=sa \
DB_PASSWORD='...' \
  ../deploy-production.sh                # schema + reference data only, no drop

DB_SERVER=myserver.example.com \
DB_USER=sa \
DB_PASSWORD='...' \
  ../deploy-production.sh --confirm-drop # prompts to confirm, then drops + recreates everything
```

It runs the exact same `01`-`07`, `97`, `99` files listed above (no duplicated SQL), skips `08-seed-data.sql` unconditionally, and only runs the three drop scripts if `--confirm-drop` is passed and the target database name is typed back at the confirmation prompt.

### Manual (SSMS / ad-hoc sqlcmd)
If you need to run these by hand against either environment, open/run the files from the table above in order — for production, skip `00`, `08`, `96`, and `98` unless you specifically intend a full reset (and have a backup).

## What Each Script Does

### 00-drop-all.sql
- **Action**: SQL Server's `DROP SCHEMA` has no CASCADE option, so this script explicitly drops all foreign keys, tables, procedures, views, and functions in the `CAMT` schema (in dependency order) before dropping the schema itself. Also drops the `dbo.BigIntIdList` table type, which lives outside `CAMT`.
- **Result**: All CAMT objects and the `dbo.BigIntIdList` type are completely removed
- **Validation**: Confirms schema and type no longer exist

### 01-schema-reference.sql
- **Creates**:
  - `CAMT` schema
  - `ReportType` reference table
  - `PaymentType` reference table
  - `ReportFrequency` reference table
- **Inserts**: Seed data for all three reference tables
- **Validates**: Row counts in reference tables

### 02-schema-sequence.sql
- **Creates**:
  - `AgreementSequence` table (per-engagement ID generation)
  - `GetNextAgreementSequence` stored procedure (with UPDLOCK/SERIALIZABLE for concurrency)
- **Tests**: Sequence generation with sample engagement ID
- **Validates**: Both objects exist and work correctly

### 03-schema-agreement.sql
- **Creates**:
  - `Recipient` (shared delivery endpoint lookup)
  - `Agreement` (master agreement records)
  - `AgreementContact` (contact information)
  - `AgreementVersion` (agreement lifecycle management)
- **Indexes**: Filtered unique indexes for active/pending versions
- **Validates**: All tables exist

### 04-schema-scope.sql
- **Creates**:
  - `AgreementScope` (defines what and where to report)
  - `PaymentTypeAssignment` (payment types covered)
  - `AccountAssignment` (bank account details)
  - `AliasAssignment` (alternative identifiers)
- **Indexes**: All necessary indexes for performance
- **Validates**: Tables exist and foreign keys are enabled

### 05-schema-report.sql
- **Creates**:
  - `ReportConfig` (report generation configuration)
  - `ReportAgreementScope` (mapping configs to scopes)
- **Constraints**: Check constraint ensuring active configs have ConfigId
- **Indexes**: Filtered unique index for ConfigId; composite index (ReportType, ReportFrequency, IsActive) for the scheduled fetch path
- **Validates**: All tables and foreign keys
- **Summary**: Displays complete list of all tables in CAMT schema

### 06-schema-audit-deadletter.sql
- **Creates**:
  - `DeadLetterMessage` (messages that failed MQ delivery)
  - `ReportCommandAudit` (audit trail of all ReportCommand messages sent, including rejection-audit rows with no resolved config/scope)
- **Indexes**: Retry lookup index on DeadLetterMessage; message/config/type/status/sent-at/job-execution indexes on ReportCommandAudit
- **Validates**: Both tables exist

### 07-schema-fetch-config.sql
- **Creates**: `dbo.BigIntIdList` — a table-valued parameter (TVP) type used to pass id-sets into fetch queries. Must match `TvpParameterSource.BIGINT_ID_LIST_TYPE` exactly.
- **Note**: Lives in the `dbo` schema, not `CAMT`.
- **Validates**: Type exists

### 08-seed-data.sql
- **Section A (happy path)**: 2 ordinary agreements, each with one scope, one payment-type assignment, and one funded account — the everyday case the fetch/assembly path spends most of its time on.
- **Section B (edge cases)**: exercises the three tree-assembly edge cases the fetch/assembly read path has to handle correctly (mirrors `ReportConfigTreeAssemblerTest`):
  - **B.1 zero-scope config** — a `ReportConfig` with no `ReportAgreementScope` rows at all
  - **B.2 dangling PTA** — a `PaymentTypeAssignment` with neither accounts nor aliases underneath it
  - **B.3 multi-scope fan-in** — one `ReportConfig` reached via scopes on two separate agreements
- **Section C (realistic multi-scope scenarios)**: 5 onboarding-style agreements from `testdata_inputs.txt` — two single-scope (account- and alias-routed payment), three dual-scope (independent report type per scope on the same agreement/version). The source input's `INSTDOM`/`INCALIAS` payment types were translated to `INSTANT_PAYMENT` (existing code) and `ALIAS_PAYMENT` (new code, added in `01-schema-reference.sql`) respectively.
- **Section D (multi-payment-type scope + remaining frequency coverage)**:
  - **D.1** — a single `AgreementScope` with two `PaymentTypeAssignment` rows (`CREDIT_TRANSFER` + `DIRECT_DEBIT`), each with its own account; also covers `EVERY_4_HOURS`
  - **D.2/D.3/D.4** — simple single-scope agreements covering the three remaining `ReportFrequency` codes no other seed data exercises: `EVERY_30_MIN`, `EVERY_2_HOURS`, `EIGHT_TIMES_PER_DAY`
- **Section E (non-active lifecycle states)**:
  - **E.1 version history** — a `REPLACED` (superseded) `AgreementVersion` alongside the current `ACTIVE` one on the same Agreement
  - **E.2 pending activation** — an Agreement whose only `AgreementVersion` is `PENDING_ACTIVATION` (no `ACTIVE` version exists yet), with a `PENDING` scope and an inactive `ReportConfig` (`ConfigId` `NULL`)
  - **E.3 cancelled scope** — an `ACTIVE` version with one `ACTIVE` scope and one `CANCELLED` scope side by side; the cancelled scope's `ReportConfig` is likewise inactive with `ConfigId` `NULL`
- **No idempotency guards**: consistent with the rest of this directory, since `00-drop-all.sql` always runs first
- **Validates**: row counts across all seeded tables; asserts the zero-scope config really has zero scopes, the fan-in config really has exactly two, the D.1 scope has exactly 2 `PaymentTypeAssignment` rows, the E.1 agreement has exactly 2 versions (one `REPLACED`), and the E.2/E.3 inactive `ReportConfig` rows really have `NULL` `ConfigId`

### 96-drop-batch.sql
- **Action**: Drops all Spring Batch (`BATCH_*`) foreign keys, tables, and identity sequences from the `dbo` schema. Scoped strictly to objects named `BATCH_%`, so it can never touch unrelated `dbo` objects.
- **Result**: All Spring Batch job repository objects are completely removed
- **Validation**: Confirms no `BATCH_*` tables or sequences remain

### 97-schema-batch.sql
- **Creates**: The 6 Spring Batch `JobRepository` metadata tables (`BATCH_JOB_INSTANCE`, `BATCH_JOB_EXECUTION`, `BATCH_JOB_EXECUTION_PARAMS`, `BATCH_STEP_EXECUTION`, `BATCH_STEP_EXECUTION_CONTEXT`, `BATCH_JOB_EXECUTION_CONTEXT`) and their 3 identity sequences (`BATCH_STEP_EXECUTION_SEQ`, `BATCH_JOB_EXECUTION_SEQ`, `BATCH_JOB_INSTANCE_SEQ`).
- **Note**: Lives in the `dbo` schema, not `CAMT`. DDL is taken verbatim from Spring Batch 6.0.4's own `schema-sqlserver.sql` (extracted from the resolved `spring-batch-core` jar), not hand-written.
- **Validates**: All tables and sequences exist

### 98-drop-quartz.sql
- **Action**: Drops all Quartz (`QRTZ_*`) foreign keys and tables from the `dbo` schema. Scoped strictly to tables named `QRTZ_%`, so it can never touch unrelated `dbo` objects.
- **Result**: All Quartz job store objects are completely removed
- **Validation**: Confirms no `QRTZ_*` tables remain

### 99-schema-quartz.sql
- **Creates**: The 11 Quartz JDBC job store tables (`QRTZ_JOB_DETAILS`, `QRTZ_TRIGGERS`, `QRTZ_SIMPLE_TRIGGERS`, `QRTZ_CRON_TRIGGERS`, `QRTZ_SIMPROP_TRIGGERS`, `QRTZ_BLOB_TRIGGERS`, `QRTZ_CALENDARS`, `QRTZ_PAUSED_TRIGGER_GRPS`, `QRTZ_FIRED_TRIGGERS`, `QRTZ_SCHEDULER_STATE`, `QRTZ_LOCKS`), their foreign keys, and their indexes.
- **Note**: Lives in the `dbo` schema, not `CAMT`. Table/column layout matches the standard Quartz SQL Server schema.
- **Validates**: All tables exist

## Schema Overview

### Complete List of Tables

| # | Table | Purpose |
|---|-------|---------|
| 1 | `ReportType` | Reference: Types of CAMT reports |
| 2 | `PaymentType` | Reference: Payment methods |
| 3 | `ReportFrequency` | Reference: Report generation frequencies |
| 4 | `Recipient` | Shared lookup for delivery endpoints |
| 5 | `Agreement` | Master agreement record |
| 6 | `AgreementContact` | Contact information for agreement |
| 7 | `AgreementVersion` | Agreement lifecycle versions |
| 8 | `AgreementScope` | Report configuration per recipient/type |
| 9 | `PaymentTypeAssignment` | Payment types covered by scope |
| 10 | `AccountAssignment` | Bank accounts linked to payment types |
| 11 | `AliasAssignment` | Alternative identifiers |
| 12 | `ReportConfig` | Report generation configuration |
| 13 | `ReportAgreementScope` | Many-to-many mapping between configs and scopes |
| 14 | `DeadLetterMessage` | Messages that failed MQ delivery |
| 15 | `ReportCommandAudit` | Audit trail of all ReportCommand messages sent |

Additionally, outside the `CAMT` schema, in `dbo`:
- `BigIntIdList` — a table-valued parameter (TVP) type, not a table
- The 6 Spring Batch `BATCH_*` job repository tables + 3 identity sequences (see `97-schema-batch.sql`)
- The 11 Quartz `QRTZ_*` job store tables (see `99-schema-quartz.sql`)

### Removed Tables
The following table from the original script has been **removed** as it was unused:
- `ReportTypeFrequency` (documentation only, no FK constraints)

## Error Handling
Each script includes:
- **Transaction wrapping**: All operations in a single transaction
- **TRY/CATCH blocks**: Explicit error handling with rollback
- **Print logging**: Detailed progress messages and timestamps
- **Validation queries**: Verify each step was successful

## Recovery/Rollback
If any script fails:
1. The transaction will automatically rollback
2. Fix the error based on the error message
3. Re-run the failed script (it will continue from where it left off)

If you need to start completely over:
1. Ensure no data has been committed (check transaction status)
2. Run `00-drop-all.sql`, `96-drop-batch.sql`, and `98-drop-quartz.sql` again to clean up (CAMT, Spring Batch, and Quartz objects respectively)
3. Start from Script 01

## Post-Deployment Verification

After all scripts complete successfully:

### 1. Check table count
Should have 15 tables in CAMT schema:
```sql
SELECT COUNT(*) FROM sys.tables WHERE SCHEMA_NAME(schema_id) = 'CAMT';
```

Should also have 6 Spring Batch tables in `dbo`:
```sql
SELECT COUNT(*) FROM sys.tables WHERE SCHEMA_NAME(schema_id) = 'dbo' AND name LIKE 'BATCH[_]%';
```

Should also have 11 Quartz tables in `dbo`:
```sql
SELECT COUNT(*) FROM sys.tables WHERE SCHEMA_NAME(schema_id) = 'dbo' AND name LIKE 'QRTZ[_]%';
```

### 2. Verify seed data counts
```sql
SELECT 'ReportType' AS TableName, COUNT(*) AS RowCount FROM CAMT.ReportType
UNION ALL
SELECT 'PaymentType', COUNT(*) FROM CAMT.PaymentType
UNION ALL
SELECT 'ReportFrequency', COUNT(*) FROM CAMT.ReportFrequency;
```

Expected results:
- ReportType: 6 rows
- PaymentType: 5 rows
- ReportFrequency: 9 rows

### 3. Check foreign keys are enabled
```sql
SELECT
    OBJECT_NAME(parent_object_id) AS TableName,
    CASE WHEN is_disabled = 1 THEN 'DISABLED' ELSE 'ENABLED' END AS Status
FROM sys.foreign_keys
WHERE SCHEMA_NAME(schema_id) = 'CAMT'
ORDER BY TableName;
```

### 4. List all objects in CAMT schema
```sql
SELECT
    TYPE_DESC AS ObjectType,
    NAME AS ObjectName
FROM sys.objects
WHERE SCHEMA_NAME(schema_id) = 'CAMT'
ORDER BY TYPE_DESC, NAME;
```

## Troubleshooting

| Error | Solution |
|-------|----------|
| "Cannot drop schema" | Ensure no other sessions are using CAMT objects. Check for open connections. |
| "Permission denied" | Use a login with db_owner or schema modification permissions |
| "Database not found" | Verify `REPORTDB` exists before running scripts |
| "Transaction count mismatch" | Run `COMMIT` or `ROLLBACK` to clear transaction state |
| "Foreign key constraint failed" | Check that referenced tables exist and have data (reference tables should have seed data) |
| "Object already exists" | You may have skipped the drop script. Run `00-drop-all.sql` first. |
| "The current transaction cannot be committed" | Check for errors in the script and ensure all operations completed successfully |

## Important Notes

### ConfigId Generation
The `ReportConfig` table uses a two-step pattern for ConfigId generation:
1. Insert with `ConfigId = NULL`
2. Application computes: `((Id * 7919) + 1234567) % 90000000 + 10000000`
3. Update with computed 8-digit value in same transaction

The `CK_ReportConfig_ActiveHasConfigId` constraint ensures active configs always have a ConfigId.

### Sequence Generation
The `GetNextAgreementSequence` stored procedure uses `UPDLOCK` and `SERIALIZABLE` hints to prevent race conditions when multiple connections request sequences simultaneously.

### Optimistic Locking
The `AgreementVersion` table includes a `Version` column (default 0) for JPA `@Version` optimistic locking.

### Status Values
These columns are validated by Java enums at the application level:
- `AgreementVersion.Status`: PENDING_ACTIVATION, PENDING_CHANGE, PENDING_CANCELLATION, ACTIVE, REPLACED, CANCELLED, EXPIRED
- `AgreementScope.Status`: PENDING, ACTIVE, CANCELLED
- `Recipient.Type`: ORIGINATOR, BIC
- `ReportCommandAudit.status`: `NVARCHAR(30)` — widened from the original `NVARCHAR(20)` to fit longer Java-only status values (e.g. `REJECTED_CONFIG_NOT_ELIGIBLE`, `REJECTED_INVALID_WINDOW`)

### Nullable Audit Columns
`ReportCommandAudit.report_config_id`, `config_id`, `agreement_scope_id`, `report_frequency`, and `mq_queue_name` are nullable, to support rejection-audit rows where no `ReportConfig`/`AgreementScope` could be resolved (e.g. recipient-not-found, config-not-found, or messages not attributable to exactly one `AgreementScope`).

## File Structure
```
infra/docker/init-scripts/
├── deploy-production.sh      # production entry point — see its header comment
└── db/
    ├── 00-drop-all.sql       # local-only
    ├── 01-schema-reference.sql
    ├── 02-schema-sequence.sql
    ├── 03-schema-agreement.sql
    ├── 04-schema-scope.sql
    ├── 05-schema-report.sql
    ├── 06-schema-audit-deadletter.sql
    ├── 07-schema-fetch-config.sql
    ├── 08-seed-data.sql      # local-only — fabricated demo data, never for production
    ├── 96-drop-batch.sql     # local-only
    ├── 97-schema-batch.sql
    ├── 98-drop-quartz.sql    # local-only
    ├── 99-schema-quartz.sql
    └── README.md
```

## Support
For issues with these scripts, contact your database administrator or development team.

## Version History
- **2026-08-07 (latest)**: Split local-development vs. production usage explicitly. Added
  `../deploy-production.sh`, which runs the same `01`-`07`/`97`/`99` files (no duplicated SQL)
  against a production target, always skips `08-seed-data.sql`, and gates the three drop
  scripts behind an explicit `--confirm-drop` flag plus a typed database-name confirmation.
  This README previously listed `08-seed-data.sql` as step 9 of "the" production deployment
  sequence with no caveat — corrected, since that script seeds fabricated demo business data
  that must never reach a real production database.
- **2026-07-30**: Extended `08-seed-data.sql` with Sections C, D, E from `testdata_inputs.txt` plus follow-up coverage
  - Section C: 5 realistic onboarding-style scenarios (single/dual-scope agreements, account- and alias-routed payment)
  - Section D: a scope with 2 `PaymentTypeAssignment` rows, plus the 3 `ReportFrequency` codes no other seed data exercised (`EVERY_30_MIN`, `EVERY_2_HOURS`, `EIGHT_TIMES_PER_DAY`)
  - Section E: non-active lifecycle states — version history (`REPLACED` -> `ACTIVE`), pending activation, cancelled scope
  - Added `ALIAS_PAYMENT` to `CAMT.PaymentType` in `01-schema-reference.sql` (source input's `INCALIAS` code didn't exist; `INSTDOM` was mapped to the existing `INSTANT_PAYMENT` code instead)
  - Added `AliasAssignment` to the Section VALIDATION row-count query (previously omitted)
- **2026-07-11**: Added `96-drop-batch.sql` / `97-schema-batch.sql` (Spring Batch `JobRepository` metadata tables + sequences, Phase 4 batch pipeline), numbered ahead of Quartz's `98`/`99` pair so both non-CAMT drop/create pairs sit together at the end of the chain, wired into `compose.yaml`
- **2026-07-11 (later)**: Added `08-seed-data.sql`, wired into the automated `compose.yaml` init chain
  - Retires the old top-level `infra/docker/init-scripts/02-seed.sql`, which had been orphaned from the init chain during the `db/` restructure
  - Section A: 2 happy-path scenarios (same data as the old seed script)
  - Section B: 3 fetch-config edge cases — zero-scope config, dangling PTA, multi-scope fan-in — mirroring `ReportConfigTreeAssemblerTest`
- **2026-07-11**: Added audit/dead-letter tables, fetch-config support, and Quartz job store
  - Added `06-schema-audit-deadletter.sql` (DeadLetterMessage, ReportCommandAudit)
  - Added `07-schema-fetch-config.sql` (dbo.BigIntIdList TVP type)
  - Added composite index `IX_ReportConfig_TypeFrequencyActive` to `05-schema-report.sql`
  - Added `98-drop-quartz.sql` / `99-schema-quartz.sql` (Quartz JDBC job store, numbered out of sequence since it's unrelated to the CAMT schema)
  - `00-drop-all.sql` now also drops `dbo.BigIntIdList`
- **2026-07-10**: Initial clean deployment scripts
  - Removed `ReportTypeFrequency` table (unused)
  - Removed all idempotency checks (`IF NOT EXISTS`)
  - Replaced `MERGE` statements with `INSERT` for seed data
  - Added comprehensive logging and transaction handling
  - Organized into 6 logical groups with execution order
  - Added `00-drop-all.sql` for complete cleanup