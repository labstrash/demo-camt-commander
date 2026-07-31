# Architecture Decisions

Running log of design decisions that aren't obvious from the code alone. Each entry: the
question raised, the decision, and the reasoning.

---

## 1. `Clock` vs `Instant` for "current time" dependencies

**Raised:** Noticed `Clock` used in the MQ/scheduling/on-demand code
(`MqCircuitBreaker`, `MqResilienceConfig`, `AuditRetentionJob`, `DeadLetterRecoveryJob`,
`OnDemandReportService`, `ReportMessageDeliveryService`) while the rest of the codebase uses
`Instant`. Question: should `Clock` be replaced with `Instant` for consistency?

**Decision:** No — keep `Clock` where it is. The two types aren't interchangeable; they serve
different roles and both are already used correctly.

**Reasoning:**

- `Instant` is a **value** — a single, frozen point in time.
- `Clock` is a **source** of `Instant` values — calling `clock.instant()` gives you "now" at
  the moment of the call, and it can be swapped (`Clock.fixed(...)`, a `MutableClock` test
  double) to make time deterministic in tests.
- The components in question need "now" evaluated fresh at call time — a circuit breaker
  checking `openUntil`, a retention job computing a cutoff, an on-demand request defaulting its
  reference time. Injecting a bare `Instant` bean instead would capture a single value at
  application startup and never advance, silently freezing "now" for the app's lifetime — a
  correctness bug, not a style choice.
- It would also break the existing testability seam: `MqCircuitBreakerTest`'s `MutableClock`
  and `OnDemandReportServiceTest`'s `Clock.fixed(NOW, ZoneOffset.UTC)` both depend on `Clock`
  being an injectable, swappable dependency.
- Every other `Instant` usage in the codebase (`ReportWindow`, `ReportingPeriodCalculator`,
  audit/dead-letter records, `sentAt`/`nextRetryAt`/window bounds) holds a *specific*
  timestamp, not a "give me current time" role — those are correctly `Instant`. There's no
  actual inconsistency: `Clock` is the injectable now-source, `Instant` is the value it
  produces, and the codebase already follows that split.
- The only real alternative considered — a `Supplier<Instant>` bean — would reinvent `Clock`
  with less capability (no `Clock.fixed`/`Clock.offset` test helpers, no timezone awareness)
  for no benefit.

**Applies to:** `MqCircuitBreaker`, `MqResilienceConfig`, `AuditRetentionJob`,
`DeadLetterRecoveryJob`, `OnDemandReportService`, `ReportMessageDeliveryService`, and any future
component that needs a testable "now."
