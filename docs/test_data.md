# Manual Test Messages — On-Demand & PHT Queues

Sample messages for manually exercising `CAMT.ONDEMAND.QUEUE` and `CAMT.PHT.QUEUE` against a
freshly seeded local environment (`infra/docker/init-scripts/db/08-seed-data.sql`, Section A).
All recipient/config/agreement values below are real seeded rows, not invented — the happy-path
messages will actually resolve and produce an outbound message.

## Before you start

1. **Enable both listeners** — both default to `false` in `application.properties`:
   ```
   commander.ondemand.enabled=true
   commander.pht.enabled=true
   ```
   Nothing consumes either queue until these are set (either edit the file or override via
   `-Dcommander.ondemand.enabled=true -Dcommander.pht.enabled=true` / env vars) and the app is
   (re)started.
2. **Seed data must be loaded** — `docker compose up` in `infra/docker/` runs `08-seed-data.sql`
   as part of `sqlserver-init`. The scenarios below use Section A's two recipients.
3. **Window timestamps are time-sensitive** — the on-demand messages below use a fixed past
   window (`2026-07-01` to `2026-07-02`). If you're reading this well after that date it's still
   safely in the past, so no change needed; just don't move `windowEndUtc` into the future or
   the request is rejected (`REJECTED_INVALID_WINDOW`).

---

## On-Demand — `CAMT.ONDEMAND.QUEUE`

JSON body, deserialized directly to `OnDemandReportRequest`. Sent as a `TextMessage`.

### 1. Happy path — resolves and sends

Uses Section A's `Recipient` "Team Nirvana A" (`ORIGINATOR` / `3937231530REP0001`) and
`ReportConfig` `10000001` (`CAMT054C`, `V02`, active, unbundled, one account under it).

```json
{
  "recipientType": "ORIGINATOR",
  "recipientValue": "3937231530REP0001",
  "reportType": "CAMT054C",
  "reportVersion": "V02",
  "windowStartUtc": "2026-07-01T00:00:00Z",
  "windowEndUtc": "2026-07-02T00:00:00Z",
  "requestorName": "manual-test"
}
```

Expected: one `ReportMessage` (unbundled config, one account → one message) delivered to
`CAMT.054C.QUEUE`, one `CAMT.ReportCommandAudit` row with `status = SENT`.

### 2. Negative — unknown recipient → `REJECTED_CONFIG_NOT_ELIGIBLE`

```json
{
  "recipientType": "ORIGINATOR",
  "recipientValue": "DOES-NOT-EXIST",
  "reportType": "CAMT054C",
  "reportVersion": "V02",
  "windowStartUtc": "2026-07-01T00:00:00Z",
  "windowEndUtc": "2026-07-02T00:00:00Z",
  "requestorName": "manual-test"
}
```

Expected: no outbound message; one audit row, `status = REJECTED_CONFIG_NOT_ELIGIBLE`.

### 3. Negative — window ends in the future → `REJECTED_INVALID_WINDOW`

```json
{
  "recipientType": "ORIGINATOR",
  "recipientValue": "3937231530REP0001",
  "reportType": "CAMT054C",
  "reportVersion": "V02",
  "windowStartUtc": "2026-07-01T00:00:00Z",
  "windowEndUtc": "2099-01-01T00:00:00Z",
  "requestorName": "manual-test"
}
```

Expected: no outbound message; one audit row, `status = REJECTED_INVALID_WINDOW`.

---

## PHT — `CAMT.PHT.QUEUE`

Semicolon-delimited plain text (see `docs/pht.txt`):
`messageLength;versionNumber;messageDate;messageTime;accountOwner;accountCount` header, followed
by `accountCount` triplets of `(clearingNumber;accountNumber;balance)`. Sent as a `TextMessage`
or `BytesMessage` — both are handled identically.

### 1. Happy path — resolves and sends

Uses Section A's `Agreement` "Agreement Two" (`EngagementId = 065561959304`), whose
`AgreementScope` is `ReportType = CAMT052B` (matches `commander.pht.report-type`'s default),
resolving to `Recipient` "Team Nirvana B" and `ReportConfig 10000002` (`CAMT052B`, `V02`,
active, **bundled**), which has exactly one account: `ClearingNumber 83279 / AccountNumber
503797518`.

```
150;01;20260802;120000;065561959304;01;83279;503797518;12345,67
```

(`messageLength`/`versionNumber`/`messageDate`/`messageTime` aren't validated against the real
message — only `accountOwner` and the account triplets matter for resolution.)

Expected: one bundled `ReportMessage` (balance `12345,67` attached to the matched account)
delivered to `CAMT.052B.QUEUE`, one `CAMT.ReportCommandAudit` row with `status = SENT`.

### 2. Negative — unknown `accountOwner` → dropped, no audit row

```
120;01;20260802;120000;000000000000;01;83279;503797518;12345,67
```

Expected: `AgreementScopeRepository` finds no match — the message is logged and dropped.
**Unlike on-demand, PHT resolution misses never write an audit row** (no request-shaped input
to attribute one to) — check the application log for `No active agreement scope for PHT
accountOwner=...` instead.

### 3. Negative — malformed wire format → dropped, no audit row

```
not a valid pht message
```

Expected: `PhtMessageParser` throws `IllegalArgumentException` inside the listener's catch-all;
logged as a parse failure, container keeps running, nothing delivered.

---

## Placing a message on a queue

Using the IBM MQ sample utility inside the `ibmmq` container (queue manager `QM1`, per
`infra/docker/compose.yaml`):

```bash
# On-demand — paste the JSON, then Ctrl-D (Linux/macOS) to send
docker exec -it ibmmq /opt/mqm/samp/bin/amqsput CAMT.ONDEMAND.QUEUE QM1

# PHT — paste the semicolon-delimited line, then Ctrl-D to send
docker exec -it ibmmq /opt/mqm/samp/bin/amqsput CAMT.PHT.QUEUE QM1
```

Each line typed before Ctrl-D becomes one message — paste a single-line JSON (or use the
multi-line form above and let `amqsput` send it as one message; a truly multi-line paste sends
one message per line, so keep the JSON on one line if pasting directly).

## Verifying the result

- **Audit trail**: `SELECT TOP 20 * FROM CAMT.ReportCommandAudit ORDER BY Id DESC;` against the
  `REPORTDB` SQL Server instance (`localhost:1433`, `sa` / `YourStrong!Passw0rd`).
- **Outbound message**: read it back off the target queue the same way —
  `docker exec -it ibmmq /opt/mqm/samp/bin/amqsget CAMT.054C.QUEUE QM1` (on-demand scenario 1) or
  `CAMT.052B.QUEUE` (PHT scenario 1) — `amqsget` drains the queue, so only do this after
  confirming the audit row.
- **Application log**: rejections/drops that don't reach delivery (on-demand's `REJECTED_*`,
  every PHT resolution miss) are logged at `WARN`/`INFO` even when no audit row is written.
