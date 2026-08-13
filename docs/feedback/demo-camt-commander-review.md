# Code Review — `demo-camt-commander` @ `phase06`, `src/main`

Scope: all 85 files / ~6,325 lines under `src/main`. Skipped `src/test` and `infra/`.

## Overall impression

This is solid work. The layering (domain → service → adapter/repository) is consistently
applied, the domain records validate their own invariants at construction, and the pure
(no-Spring) classes — `ReportMessageAssembler`, `ReportConfigTreeAssembler`, `ExtMessageParser`,
`ReportingPeriodCalculator` — are genuinely unit-testable without a container. The MQ resilience
stack (`MqCircuitBreaker` / `MqFailureClassifier` / `ResilientMqSender` / `SendOutcome`) is a
clean, single-responsibility decomposition that most teams don't bother with.

Given the brief ("simple but maintainable"), my main pushback is that the codebase is carrying a
*lot* of prose — javadoc paragraphs that re-explain design decisions rather than document the
API — and a few places where that same explanatory effort would have been better spent just
deduplicating logic or wiring up validation that's already written but never called. Below is
everything I'd actually act on, roughly in priority order.

---

## 1. Bugs / correctness gaps (fix these)

### 1.1 `SchedulingProperties.validate()` is dead code
`SchedulingProperties` has a thorough `validate()` method — checks exactly-one-of cron/boundaries,
duplicate `(reportType, frequency)` pairs, ascending boundaries, etc. But nothing calls it:

```
grep -rn "schedulingProperties.validate\|\.validate()" src/main   →  no hits except a javadoc mention
```

It's not wired via `@PostConstruct`, not called from `BatchPipelineConfig`, not called anywhere.
Right now a misconfigured `application.properties` (duplicate report-type/frequency pair, a
boundary schedule with `window-minutes` set, boundaries out of order) will pass silently through
startup and only surface as a confusing runtime failure — or wrong report windows — much later.
This is the single highest-value fix in the codebase: it's already written, just not invoked.

**Fix:**
```java
@PostConstruct
void validateOnStartup() {
    validate();
}
```
placed directly on `SchedulingProperties`, matching the pattern `MqResilienceProperties` already
uses (`flattenAndValidateTiers()` is `@PostConstruct`).

### 1.2 `application-testenv.properties` doesn't do what its own comment says
The file's header comment says:

> No real values belong in this file: every placeholder below is resolved from an environment
> variable at startup, so this file is safe to commit as-is.

But the body is:
```properties
spring.datasource.url=jdbc:sqlserver://localhost:1433;databaseName=REPORTDB;...
spring.datasource.username=sa
spring.datasource.password=YourStrong!Passw0rd
```
No `${TESTENV_DB_URL}` placeholders anywhere — it's a byte-for-byte copy of the default
`application.properties` datasource block with hardcoded local-dev credentials. Either the
profile was never finished, or a `git add` picked up the wrong version. As-is, activating
`testenv` changes nothing and the comment is actively misleading about what "safe to commit"
means here.

**Fix:** either implement the `${TESTENV_DB_URL}` / `${TESTENV_DB_USERNAME}` /
`${TESTENV_DB_PASSWORD}` placeholders the comment promises, or delete the aspirational comment
and treat this file as a local scratch profile.

### 1.3 Plaintext credentials committed in `application.properties`
```properties
spring.datasource.password=YourStrong!Passw0rd
ibm.mq.password=passw0rd
```
Fine for a local docker-compose sandbox, but worth flagging now before this pattern calcs into
muscle memory for a real environment file. Since `platform-catalog`/`platform-core` are still
being stood up, this would be a good moment to standardize on env-var-sourced secrets
(`${DB_PASSWORD}`) across every service in `siritechlabs`, rather than retrofitting it later.

### 1.4 Referenced-but-missing `DeadLetterRecoveryJob` / `AuditRetentionJob`
Javadoc in `ReportCommandAuditRepository`, `DeadLetterMessageRepository`,
`ReportMessageDeliveryService`, and `AuditRetentionProperties` all refer to a
`DeadLetterRecoveryJob` and an `AuditRetentionJob` as existing collaborators — neither class
exists in `src/main`. `MqResilienceProperties` even ships a fully-modeled, validated
`deadLetterRetryBackoffTiers` config surface for a job that isn't there yet.

I'm assuming this is intentional — deferred to a later phase per the "Phase 3 doc, Decision 6"
references — but flagging it because right now a dead-lettered message has **no automated path
back to the queue**: `DeadLetterMessageRepository.findDueForRetry()` is written and correct, but
nothing ever calls it. If phase06 is meant to ship as-is, it's worth a one-line note in the
package or a `// TODO(phase-N)` near `deliver()`'s dead-letter branch, so it doesn't read as an
oversight to the next person who opens this file.

---

## 2. Duplication (the "simple but maintainable" ask)

### 2.1 `singleRow()` is copy-pasted verbatim
`AgreementScopeRepositoryImpl` and `ReportConfigRepositoryImpl` each define their own private
`singleRow(List<T>)` helper — identical shape, near-identical Javadoc, only the exception message
differs:

```java
// AgreementScopeRepositoryImpl
private static <T> Optional<T> singleRow(List<T> rows) { ... "EngagementId is not guaranteed unique..." }

// ReportConfigRepositoryImpl
private static <T> Optional<T> singleRow(List<T> rows) { ... "unique-constraint violation..." }
```

**Fix:** pull this into a shared utility, matching the precedent `ConfigurationRowMappers`
already sets for centralizing repository-layer boilerplate:

```java
public final class JdbcRepositorySupport {
    private JdbcRepositorySupport() {}

    public static <T> Optional<T> singleRow(List<T> rows, String context) {
        if (rows.isEmpty()) return Optional.empty();
        if (rows.size() > 1) {
            throw new IllegalStateException(
                "Expected at most one row but found " + rows.size() + " — " + context);
        }
        return Optional.of(rows.get(0));
    }
}
```
Two call sites today, but a third repository is a `git blame` away from copying it a third time
if it isn't consolidated now.

### 2.2 Boundary-time parsing duplicated between two classes
`ReportingPeriodCalculator.parseBoundaries(String)` and `SchedulingProperties.parseBoundaries(String)`
are functionally identical — split on `,`, trim, filter blank, parse `HH:mm` — implemented twice,
once in the domain layer and once in the config layer:

```java
// ReportingPeriodCalculator
private static List<LocalTime> parseBoundaries(String boundaries) {
    if (boundaries == null || boundaries.isBlank()) return List.of();
    return Arrays.stream(boundaries.split(","))
            .map(String::trim).filter(v -> !v.isEmpty())
            .map(v -> LocalTime.parse(v, BOUNDARY_TIME_FORMAT)).toList();
}

// SchedulingProperties — same logic, different formatter instance
```
**Fix:** move this to a single static method (e.g. on `ReportFrequency` or a small
`BoundaryTimes` helper in `domain.report`) and have both classes call it. This also guarantees
the two call sites can never silently drift on parsing rules (e.g. one accepting `H:mm` and the
other not).

### 2.3 Minor: `.get(0)` vs `.getFirst()` inconsistency
`OnDemandReportService.triggerForConfig`:
```java
ReportConfigTree tree = reportConfigTreeRepository.assembleTrees(List.of(config)).get(0);
```
`ExtReportOrchestrationService.process`:
```java
ReportConfigTree tree = reportConfigTreeRepository.assembleTrees(List.of(config.get())).getFirst();
```
Same operation, same single-element-list precondition, different API. Not a bug, but worth a
find-and-replace to `.getFirst()` for consistency (and it documents the "exactly one" assumption
slightly better than `.get(0)` does).

### 2.4 `import java.util.*;` wildcard in `SchedulingProperties.java`
Every other file in the codebase uses explicit imports (and Spotless's `removeUnusedImports` /
`importOrder` are already configured in `build.gradle`). `SchedulingProperties` is the one file
with a wildcard import — looks like it'll get silently reformatted the next time Spotless runs
over it, but worth fixing by hand now so a diff doesn't show up unexpectedly in an unrelated PR.

---

## 3. Design notes (not bugs — things to be deliberate about)

### 3.1 `ReportMessageIdGenerator.extractTypeCode` — you already flagged this yourself
```java
private String extractTypeCode(String reportType) {
    // Extract from position 5 (0-indexed)... Safe unconditionally — every ReportType
    // constant name is at least 8 characters.
    // This is fragile - consider using a more explicit mapping in future
    return reportType.substring(5);
}
```
Agreed — this is the one piece of "magic number" logic in an otherwise very explicit codebase.
The class javadoc already does the budget math (`PREFIX 6 + reportId 8 + timestamp 13 +
pageNumber 4` = 31, leaving exactly 3 chars for `CAMT052BT` → `52BT` = 4, landing at the 35-char
ceiling), which tells me this was already reasoned through carefully — but it depends on every
future `ReportType` constant name being long enough, and on nobody adding a report type whose
suffix needs a 5th character.

Since `ReportType` is a closed enum you control, I'd make this self-documenting and compiler-
enforced instead of relying on a substring offset + a comment:

```java
public enum ReportType {
    CAMT052B("52B"), CAMT052BT("52BT"), CAMT053S("53S"),
    CAMT053E("53E"), CAMT054D("54D"), CAMT054C("54C");

    private final String code;
    ReportType(String code) { this.code = code; }
    public String code() { return code; }
}
```
Then `ReportMessageIdGenerator` calls `reportType.code()` — no substring arithmetic, and adding a
new report type forces you to explicitly pick its code at the same call site, rather than trusting
`substring(5)` to keep working.

### 3.2 `ReportPipelineItemReader`'s buffering/checkpoint logic
This class does a lot: paginated repository reads, tree assembly, per-tree message buffering, and
a "checkpoint only fully-drained trees" invariant to avoid data loss on restart. The javadoc
explaining *why* `lastSeenId` only advances after a tree is drained (not merely assembled) is
excellent — genuinely the kind of thing that saves a future incident. But the class itself is
doing reader + buffering-strategy + checkpoint-strategy in one place.

Not urgent, but if this class grows further (e.g. once the dead-letter recovery reader or
audit-retention reader show up and need similar page-buffering), I'd extract the
`Deque<TreeGroup>` + `advanceEagerlyThroughEmptyGroups()` logic into a small standalone
`DrainableGroupBuffer<K, T>` that's independently testable without a `StepScope`/Spring Batch
context. Right now testing the checkpoint edge cases (zero-fan-out trees at a page boundary,
etc.) requires standing up the full reader.

### 3.3 Hand-rolled builders vs. records
`ReportMessage.Builder` and `ReportCommandAuditEntry.Builder` are both ~15-field hand-written
builders. Given the project's stated preference for mechanical enforcement over
discipline/tooling (per your working style), and that Lombok is deliberately absent from
`build.gradle`, this is a reasonable trade — I wouldn't change it. Worth calling out only because
it's ~150 lines of near-identical boilerplate across the two builders; if a third wide record
shows up, it might be worth a tiny code-generation step (or just accepting the boilerplate,
which is a legitimate choice too).

---

## 4. What's working well (keep doing this)

- **UTC timestamp handling.** Every JDBC repository binds/reads `DATETIME2` columns with an
  explicit `Calendar.getInstance(UTC)`. This is the correct fix for a real, easy-to-miss bug
  (JVM-default-timezone coercion on `PreparedStatement.setTimestamp`), applied consistently, and
  documented once with a cross-reference from the second file rather than re-explained. Exactly
  the right amount of documentation for a non-obvious footgun.
- **Domain records validate themselves.** `Recipient`, `ReportMessage`, `ReportWindow`,
  `PaymentTypeAllocation` etc. all reject invalid state in their compact constructors. This means
  invariants (e.g. "bundled messages must have null scopeId") can't be violated by a caller
  forgetting a check — the type system + constructor does it once, everywhere.
- **One delivery path, three trigger sources.** `ReportMessageDeliveryService.deliver()` is
  reused unchanged by the batch writer, the on-demand listener, and the EXT orchestration
  service. This is exactly the kind of thing that prevents "the on-demand path quietly handles
  retries differently" bugs six months from now.
- **TVP usage for unbounded `IN (...)` lists.** Recognizing that scopes are page-bounded (safe
  for a plain `IN`) but assignments/accounts/aliases aren't, and switching strategy accordingly,
  is a level of SQL Server-specific care that's easy to skip and expensive to discover in
  production.

---

## Suggested priority order

1. Wire up `SchedulingProperties.validate()` (§1.1) — five-minute fix, closes a real fail-fast gap.
2. Fix or delete the misleading `application-testenv.properties` comment (§1.2).
3. Extract `singleRow()` (§2.1) and the boundary parser (§2.2) — mechanical, low-risk.
4. Decide whether the dead-letter-recovery/audit-retention gap (§1.4) needs a `// TODO` marker
   before this branch merges, or is already tracked elsewhere.
5. Everything in §3 is discretionary — worth a team discussion, not a blocking fix.
