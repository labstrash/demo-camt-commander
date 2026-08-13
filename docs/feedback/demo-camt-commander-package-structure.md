# Proposed package structure — `demo-camt-commander` (`phase06`)

## The core problem with the current layout

Two packages are doing too much:

- **`service`** (14 classes) mixes two genuinely different kinds of code: pure business logic
  with zero framework dependencies (`ReportMessageAssembler`, `PaymentTypeGrouper`,
  `CorrelationIdGenerator`, the grouping strategies...) and orchestration services that call out
  to repositories/adapters (`OnDemandReportService`, `ExtReportOrchestrationService`,
  `ReportMessageDeliveryService`). Today nothing in the package name tells you which is which —
  you have to read the constructor to find out if a class touches the database.
- **`repository`** holds the ports (interfaces) and the JDBC adapters (`impl/`) under the same
  root, alongside — but structurally unrelated to — `adapter/`, where every *other* adapter in
  the app lives (batch, MQ). A JDBC repository implementation is exactly as much an adapter as
  `MqReportMessageWriter` is; it just isn't named or located like one.

There's also config-properties sprawl: `SchedulingProperties`/`ReportConfigReadProperties` sit in
a top-level `config/` package, `AuditRetentionProperties` sits alone in a top-level `audit/`
package, and `MqProperties`/`MqResilienceProperties`/`BatchPipelineProperties` sit inside their
own adapter packages — four different locations for the same kind of class, with no consistent
rule for which one a new properties class should go in.

The structure below fixes this by making the **hexagonal boundary the primary grouping
principle** — `domain` (pure), `application` (use-case orchestration over ports), `port`
(interfaces), `adapter` (implementations) — with properties classes colocated with whichever
adapter/scheduling concern they configure, rather than parked at the root.

---

## Proposed tree

```
com.example.commander
│
├── DemoCamtCommanderApplication.java
│
├── domain/                                    # zero JDBC/JMS/Batch/HTTP dependencies
│   │
│   ├── config/                                # (unchanged) assembled hierarchy model
│   │   ├── AccountAssignmentRow
│   │   ├── AgreementScopeNode
│   │   ├── AgreementScopeRow
│   │   ├── AliasAssignmentRow
│   │   ├── PaymentTypeAssignmentNode
│   │   ├── PaymentTypeAssignmentRow
│   │   ├── RecipientRow
│   │   ├── ReportConfigRow
│   │   ├── ReportConfigTree
│   │   └── ReportConfigTreeAssembler
│   │
│   ├── message/                                # (unchanged) outbound message value model
│   │   ├── AccountAllocation
│   │   ├── AccountBalance
│   │   ├── AccountKey
│   │   ├── AliasAllocation
│   │   ├── AssemblyContext
│   │   ├── PaymentTypeAllocation
│   │   ├── Recipient
│   │   ├── RecipientType
│   │   ├── ReportContext
│   │   ├── ReportMessage
│   │   ├── ReportMessageEnvelope
│   │   ├── ReportType
│   │   ├── ScopedAllocation
│   │   └── TriggerType
│   │
│   ├── assembly/                               # ★ NEW — pulled out of `service`
│   │   ├── AllocationMapper
│   │   ├── BundledGroupingStrategy
│   │   ├── CorrelationIdGenerator
│   │   ├── MessageGroupingStrategy
│   │   ├── MessageGroupingStrategyFactory
│   │   ├── MessageIdValidator
│   │   ├── OutboundMessageBuilder
│   │   ├── PaymentTypeGrouper
│   │   ├── ReportMessageAssembler
│   │   ├── ReportMessageIdGenerator            # ★ moved out of domain.message
│   │   └── UnbundledGroupingStrategy
│   │
│   ├── audit/                                  # (unchanged)
│   │   ├── ReportCommandAuditEntry
│   │   └── ReportCommandAuditStatus
│   │
│   ├── deadletter/                             # (unchanged)
│   │   └── DeadLetterMessage
│   │
│   ├── ondemand/                                # (unchanged)
│   │   ├── OnDemandMessageOutcome
│   │   ├── OnDemandReportRequest
│   │   └── OnDemandReportResult
│   │
│   ├── ext/                                     # ★ parser moved in — see note below
│   │   ├── ExtAccountBalance
│   │   ├── ExtBalanceMessage
│   │   └── ExtMessageParser                     # ★ moved from `service`
│   │
│   └── report/                                  # (unchanged)
│       ├── ReportFrequency
│       ├── ReportWindow
│       └── ReportingPeriodCalculator
│
├── application/                                 # ★ NEW — use-case orchestration over ports
│   ├── OnDemandReportService
│   ├── ExtReportOrchestrationService
│   └── ReportMessageDeliveryService
│
├── port/                                        # ★ NEW — moved out of `repository` root
│   ├── AgreementScopeRepository
│   ├── DeadLetterMessageRepository
│   ├── ReportCommandAuditRepository
│   ├── ReportConfigRepository
│   └── ReportConfigTreeRepository
│
└── adapter/
    │
    ├── persistence/                             # ★ renamed from `repository.impl`
    │   ├── AgreementScopeRepositoryImpl
    │   ├── ConfigurationRowMappers
    │   ├── DeadLetterMessageRepositoryImpl
    │   ├── ReportCommandAuditRepositoryImpl
    │   ├── ReportConfigRepositoryImpl
    │   ├── ReportConfigTreeRepositoryImpl
    │   ├── ReportConfigReadProperties           # ★ moved from top-level `config`
    │   └── tvp/
    │       └── TvpParameterSource
    │
    ├── batch/                                   # (unchanged)
    │   ├── config/
    │   │   ├── BatchPipelineConfig
    │   │   └── BatchPipelineProperties
    │   ├── processor/
    │   │   └── RecipientResolvingReportMessageProcessor
    │   ├── reader/
    │   │   └── ReportPipelineItemReader
    │   ├── writer/
    │   │   ├── CompositeReportMessageWriter
    │   │   ├── LoggingReportMessageWriter
    │   │   └── MqReportMessageWriter
    │   └── trigger/
    │       └── ReportPipelineTrigger
    │
    ├── scheduling/                               # ★ absorbs top-level `scheduling` + `audit` + half of `config`
    │   ├── PeriodicReportPipelineRunner
    │   ├── SchedulingProperties                  # ★ moved from top-level `config`
    │   └── AuditRetentionProperties               # ★ moved from top-level `audit`
    │
    └── message/                                  # (unchanged)
        ├── InboundMqListenerConfig
        ├── JmsMessageBodyReader
        ├── MqCircuitBreaker
        ├── MqFailureClassifier
        ├── MqProperties
        ├── MqResilienceConfig
        ├── MqResilienceProperties
        ├── PermanentMqFailureException
        ├── ResilientMqSender
        ├── SendOutcome
        ├── TransientMqFailureException
        ├── ondemand/
        │   ├── OnDemandMessageListener
        │   └── OnDemandProperties
        └── ext/
            ├── ExtMessageListener
            └── ExtProperties
```

Top-level `config/` and `audit/` are gone entirely — every properties class now lives next to the
adapter concern it configures.

---

## Why each move

### `service` → split into `domain.assembly` (11 classes) and `application` (3 classes)

I went through every class's constructor dependencies. The split isn't a judgment call — it falls
out cleanly:

| Class | Depends on a port/adapter? | New home |
|---|---|---|
| `AllocationMapper` | no | `domain.assembly` |
| `BundledGroupingStrategy` | no (→ `PaymentTypeGrouper`) | `domain.assembly` |
| `CorrelationIdGenerator` | no | `domain.assembly` |
| `MessageGroupingStrategy` | no | `domain.assembly` |
| `MessageGroupingStrategyFactory` | no | `domain.assembly` |
| `MessageIdValidator` | no | `domain.assembly` |
| `OutboundMessageBuilder` | no (→ 3 generators above, all pure) | `domain.assembly` |
| `PaymentTypeGrouper` | no | `domain.assembly` |
| `ExtMessageParser` | no | `domain.ext` (colocated with `ExtBalanceMessage`, which it produces) |
| `ReportMessageAssembler` | no (→ `OutboundMessageBuilder` + factory, both pure) | `domain.assembly` |
| `UnbundledGroupingStrategy` | no (→ `PaymentTypeGrouper`) | `domain.assembly` |
| `OnDemandReportService` | **yes** — `ReportConfigRepository`, `ReportConfigTreeRepository`, `ReportCommandAuditRepository` | `application` |
| `ExtReportOrchestrationService` | **yes** — `AgreementScopeRepository`, `ReportConfigRepository`, `ReportConfigTreeRepository` | `application` |
| `ReportMessageDeliveryService` | **yes** — `DeadLetterMessageRepository`, `ReportCommandAuditRepository`, `ResilientMqSender` | `application` |

This is the single most useful split in this whole proposal: `ReportMessageAssembler` — the class
that decides bundled vs. unbundled fan-out — turns out to have **no database dependency at all**.
It was sitting in `service` next to three classes that talk to the database on every call, which
made that fact easy to miss. Once it's in `domain.assembly`, that's structurally obvious, and an
ArchUnit rule can enforce it stays that way (see below).

### `ReportMessageIdGenerator`: `domain.message` → `domain.assembly`

It's an ID-generation algorithm, not a value object — it doesn't belong next to `ReportMessage`/
`Recipient`/etc., it belongs next to the other generators it's used alongside
(`CorrelationIdGenerator`, `MessageIdValidator`) inside the fan-out pipeline. This leaves
`domain.message` as a clean, pure data-model package with no `@Component`-annotated generators
mixed in.

### `repository` (interfaces) → `port`; `repository.impl` → `adapter.persistence`

Same principle as the `service` split, applied to the other half of the codebase: a port
(`ReportConfigRepository`) and its adapter (`ReportConfigRepositoryImpl`) shouldn't share a root
package that has no sibling relationship to `adapter/`. Renaming `repository.impl` to
`adapter.persistence` makes it a peer of `adapter.batch` and `adapter.message`, which is what it
actually is — a third kind of adapter (persistence, alongside batch and messaging). Interface and
class names are unchanged; this is purely a package move.

### Top-level `config/` and `audit/` → folded into the adapter/scheduling package they configure

- `ReportConfigReadProperties` only configures `ReportConfigTreeRepositoryImpl`'s paging/timeout
  behavior → `adapter.persistence`.
- `AuditRetentionProperties` backs a not-yet-built retention job that will almost certainly be a
  scheduled job like `PeriodicReportPipelineRunner` → `adapter.scheduling`.
- `SchedulingProperties` → `adapter.scheduling`, alongside `PeriodicReportPipelineRunner` and
  `ReportPipelineTrigger` (`adapter.batch.trigger`), which are its two real consumers.

### `scheduling` (top-level) → `adapter.scheduling`

`PeriodicReportPipelineRunner` is a `@Scheduled` cron-driven adapter — the explicit stand-in for
Quartz described in its own javadoc. It belongs under `adapter` for the same reason
`MqReportMessageWriter` does: it's a framework-driven entry point into the application, not
business logic.

---

## One thing this reorganization surfaces (worth a follow-up, not fixed here)

`domain.report.ReportingPeriodCalculator` — a pure domain class — takes `SchedulingProperties` as
a constructor dependency. Once `SchedulingProperties` moves to `adapter.scheduling`, that
dependency becomes a **visible domain → adapter violation**, not just a semantic one. It was
already true before this reorganization (`domain.report` → `config` is exactly as much a layer
violation as `domain.report` → `adapter.scheduling`); the new package names just make it
impossible to miss, which is the point.

The fix, when you get to it, is small: give `domain.report` its own narrow interface —

```java
// domain.report
public interface ScheduleBoundaryLookup {
    List<LocalTime> boundariesFor(ReportFrequency frequency, ReportType reportType);
}
```

— and have `ReportingPeriodCalculator` depend on that instead of on `SchedulingProperties`
directly. `adapter.scheduling` then supplies an implementation that wraps `SchedulingProperties`.
I'd treat this as a small follow-up ticket rather than part of this package move, since it changes
a constructor signature and needs its own test pass — but flagging it now since the new
structure is what exposes it.

---

## What this buys you for the Port Promotion Rule / ArchUnit

With this layout, the rules you already want to enforce become one-liners:

```java
noClasses().that().resideInAPackage("..domain..")
    .should().dependOnClassesThat().resideInAnyPackage("..adapter..", "..application..");

noClasses().that().resideInAPackage("..domain.assembly..")
    .should().dependOnClassesThat().resideInAPackage("..port..");   // assembly stays pure

classes().that().resideInAPackage("..application..")
    .should().onlyBeAccessed().byClassesThat().resideInAnyPackage("..adapter..", "..application..");
```

The first rule would fail today for `domain.report.ReportingPeriodCalculator` — which, per above,
is a real finding, not a false positive to suppress.
