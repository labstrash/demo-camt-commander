#!/usr/bin/env bash
set -euo pipefail

# =============================================================================
# SCRIPT: deploy-production.sh
# PURPOSE: Deploy the CAMT schema (+ Spring Batch + Quartz job-store tables)
#          to a production SQL Server, using the exact same .sql files
#          infra/docker/compose.yaml's local sqlserver-init container runs -
#          no duplicated schema, just a different subset/order for this
#          environment:
#
#            - Runs 01-07, 96-99 (core schema + reference/lookup seed data,
#              Spring Batch tables, Quartz job store) - identical to local.
#            - NEVER runs 08-seed-data.sql - that's fabricated demo/dev
#              business data (Agreements, Recipients, ReportConfigs), not
#              appropriate for a real production database under any
#              circumstances.
#            - Only runs the three destructive drop scripts (00-drop-all,
#              96-drop-batch, 98-drop-quartz) if explicitly confirmed via
#              --confirm-drop plus typing the target database name back -
#              these scripts have no IF NOT EXISTS guards by design, so a
#              re-run without dropping first fails loudly on "already
#              exists" rather than silently corrupting anything.
#
# USAGE:
#   DB_SERVER=myserver.example.com \
#   DB_USER=sa \
#   DB_PASSWORD='...' \
#     ./deploy-production.sh                  # schema-only, no drop
#
#   DB_SERVER=myserver.example.com \
#   DB_USER=sa \
#   DB_PASSWORD='...' \
#     ./deploy-production.sh --confirm-drop    # drop everything first, then deploy
#
# REQUIRED ENVIRONMENT VARIABLES:
#   DB_SERVER    - target SQL Server host (and port, e.g. myserver.example.com,1433)
#   DB_USER      - login with db_owner or schema-modification permissions
#   DB_PASSWORD  - login password
#
# OPTIONAL ENVIRONMENT VARIABLES:
#   DB_NAME            - target database (default: REPORTDB)
#   SQLCMD_BIN         - sqlcmd binary/path (default: sqlcmd, assumed on PATH)
#   TRUST_SERVER_CERT  - set to "true" to pass sqlcmd -C (trust the server's
#                        TLS certificate without validating it). Only use
#                        this for a self-signed/internal cert you already
#                        trust out-of-band - leave unset for a properly
#                        certificate-backed production server.
# =============================================================================

DB_SERVER="${DB_SERVER:?DB_SERVER is required, e.g. myserver.example.com,1433}"
DB_NAME="${DB_NAME:-REPORTDB}"
DB_USER="${DB_USER:?DB_USER is required}"
DB_PASSWORD="${DB_PASSWORD:?DB_PASSWORD is required}"
SQLCMD_BIN="${SQLCMD_BIN:-sqlcmd}"
TRUST_SERVER_CERT="${TRUST_SERVER_CERT:-false}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/db"

CONFIRM_DROP=0
for arg in "$@"; do
    case "$arg" in
        --confirm-drop) CONFIRM_DROP=1 ;;
        *)
            echo "Unknown argument: $arg" >&2
            echo "Usage: $0 [--confirm-drop]" >&2
            exit 1
            ;;
    esac
done

SQLCMD_ARGS=(-S "$DB_SERVER" -d "$DB_NAME" -U "$DB_USER" -P "$DB_PASSWORD" -b)
if [ "$TRUST_SERVER_CERT" = "true" ]; then
    SQLCMD_ARGS+=(-C)
fi

run_script() {
    local file="$1"
    echo ">>> Running ${file} ..."
    "$SQLCMD_BIN" "${SQLCMD_ARGS[@]}" -i "${SCRIPT_DIR}/${file}"
}

echo "========================================"
echo "CAMT PRODUCTION DEPLOYMENT"
echo "Target: ${DB_SERVER} / ${DB_NAME}"
echo "Mode:   $([ "$CONFIRM_DROP" -eq 1 ] && echo 'DROP + CREATE' || echo 'CREATE ONLY (no drop)')"
echo "========================================"
echo ""

if [ "$CONFIRM_DROP" -eq 1 ]; then
    echo "!!! --confirm-drop was supplied. This will PERMANENTLY drop every CAMT"
    echo "!!! object, every Spring Batch (BATCH_*) table, and every Quartz (QRTZ_*)"
    echo "!!! table in ${DB_SERVER}/${DB_NAME}, including any data currently in them."
    echo ""
    read -r -p "Type the database name (${DB_NAME}) to confirm, anything else aborts: " CONFIRM_NAME
    if [ "$CONFIRM_NAME" != "$DB_NAME" ]; then
        echo "Aborted - confirmation did not match \"${DB_NAME}\"."
        exit 1
    fi
    echo ""
    run_script "00-drop-all.sql"
    run_script "96-drop-batch.sql"
    run_script "98-drop-quartz.sql"
else
    echo "Skipping the drop scripts (00-drop-all.sql, 96-drop-batch.sql, 98-drop-quartz.sql)."
    echo "Pass --confirm-drop to include them. Without a prior drop, the CREATE"
    echo "scripts below will fail loudly if the target objects already exist -"
    echo "they have no IF NOT EXISTS guards by design."
fi

echo ""
run_script "01-schema-reference.sql"
run_script "02-schema-sequence.sql"
run_script "03-schema-agreement.sql"
run_script "04-schema-scope.sql"
run_script "05-schema-report.sql"
run_script "06-schema-audit-deadletter.sql"
run_script "07-schema-fetch-config.sql"
run_script "97-schema-batch.sql"
run_script "99-schema-quartz.sql"

echo ""
echo "========================================"
echo "PRODUCTION DEPLOYMENT COMPLETE"
echo "08-seed-data.sql was NOT run - it seeds fabricated demo/dev business"
echo "data and must never be run against production."
echo "========================================"
