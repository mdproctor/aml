# Layer 5 Design: AML Investigation with casehub-engine

**Issue:** casehubio/aml#31
**Branch:** issue-31-layer5-engine
**Date:** 2026-05-24

---

## What Layer 5 Teaches

Layer 4 wired a fixed sequential pipeline inside `QhorusAmlInvestigator.investigate()`:
entity → pattern → OSINT, in that order, hardcoded in Java. Layer 5 replaces the
coordinator entirely with casehub-engine. The engine evaluates ALL binding conditions
simultaneously on every context update.

PEP routing fires automatically when `.entityResolution.entityType == "PEP"`. Pattern
analysis and OSINT screening run in parallel because both conditions become true at the
same instant after entity resolution completes. OSINT always declines — that outcome is a
first-class context write (`osintScreening.declined: true`), and SAR drafting proceeds
regardless.

The Merkle chain from Layer 4 is unaffected. The engine's event log provides the internal
investigation audit trail. Same ledger infrastructure, adaptive path on top.

---

## Domain Model Change (`api/`)

`EntityResolutionResult` currently: `String entityId`, `String ownershipChain`.

Add: `String entityType` (e.g. `"CORPORATE"`, `"PEP"`, `"SHELL_COMPANY"`) and
`double riskScore` (0.0–1.0).

These are natural entity resolution outputs — always were, just not modelled yet because
no layer needed them. This is a breaking record change. `NaiveEntityResolutionService`
and `EntityResolutionBehaviour` are updated in the same PR. No other callers affected.

---

## YAML Case Definition

**Location:** `app/src/main/resources/aml/aml-investigation.yaml`

### Capabilities (5)

| Capability | inputSchema (JQ) | outputSchema (JQ) |
|-----------|-------------------|-------------------|
| `entity-resolution` | `{ transaction: .transaction }` | `{ entityResolution: . }` |
| `pattern-analysis` | `{ transaction: .transaction, entityGraph: .entityResolution.ownershipChain }` | `{ patternAnalysis: . }` |
| `osint-screening` | `{ transaction: .transaction }` | `{ osintScreening: . }` |
| `senior-analyst-review` | `{ transaction: .transaction, entityResolution: .entityResolution }` | `{ seniorAnalystReview: . }` |
| `sar-drafting` | `{ transaction: .transaction, entityResolution: .entityResolution, patternAnalysis: .patternAnalysis, osintScreening: .osintScreening }` | `{ sarNarrative: .sarNarrative, complianceTaskId: .complianceTaskId }` |

### Bindings (5)

All use `on: { contextChange: {} }` trigger with binding-level `when:` conditions.

| Binding | `when:` condition |
|---------|------------------|
| `entity-resolution` | `.transaction != null and .entityResolution == null` |
| `pattern-analysis` | `.entityResolution != null and .patternAnalysis == null` |
| `osint-screening` | `.entityResolution != null and .osintScreening == null` |
| `senior-analyst-required` | `.entityResolution != null and (.entityResolution.entityType == "PEP" or .entityResolution.riskScore > 0.8) and .seniorAnalystReview == null` |
| `sar-drafting` | `.entityResolution != null and .patternAnalysis != null and .osintScreening != null and .sarNarrative == null` |

### Goal and Completion

Single goal `investigation-complete`: condition `.complianceTaskId != null`.
Completion: `allOf: [investigation-complete]`.

The SAR drafting worker calls `ComplianceReviewLifecycle.openReview()` directly and writes
both `sarNarrative` and `complianceTaskId` to context. No `humanTask` binding target —
Layer 5 teaches adaptive paths, not the HITL bridge.

### Investigation paths

**Non-PEP transaction:**
1. `entity-resolution` fires → writes entityResolution
2. `pattern-analysis` AND `osint-screening` fire in parallel (both conditions met simultaneously)
3. `sar-drafting` fires → creates WorkItem, writes sarNarrative + complianceTaskId
4. `investigation-complete` goal met

**PEP transaction (entityType == "PEP" or riskScore > 0.8):**
1. `entity-resolution` fires → writes entityResolution with PEP marker
2. `pattern-analysis` AND `osint-screening` AND `senior-analyst-required` fire in parallel (3 simultaneous bindings)
3. `sar-drafting` fires after entity + pattern + osint complete
4. `investigation-complete` goal met

OSINT always declines in the tutorial stubs. `osintScreening.declined = true` satisfies
`.osintScreening != null`, so sar-drafting fires normally. The SAR narrative notes the
declination.

---

## New Java Package: `io.casehub.aml.engine`

### `AmlInvestigationCaseHub`

`@ApplicationScoped extends YamlCaseHub("aml/aml-investigation.yaml")`.

Injects `ComplianceReviewLifecycle` and `Instance<AgentBehaviour>`. Overrides
`getDefinition()` with double-checked locking to augment the loaded YAML definition
with 5 programmatic workers after first load.

Each worker: `Worker.builder().name(...).capabilities(...).function(input -> {...}).build()`.
The function lambda captures injected services. Functions run in Quartz worker threads
(not Vert.x IO threads) — JPA calls from `ComplianceReviewLifecycle` are safe.

**Workers:**

| Worker name | Implementation |
|------------|----------------|
| `entity-resolution-agent` | Reads `transaction.flagReason`; if contains "PEP" → `entityType="PEP"`, `riskScore=0.87`; else → `entityType="CORPORATE"`, `riskScore=0.35` |
| `pattern-analysis-agent` | Delegates to `NaivePatternAnalysisService` stub |
| `osint-screening-agent` | Delegates to `OsintScreeningBehaviour.handle(null)` which always returns `Declined`; writes `{declined:true, reason:"insufficient clearance for PEP database access", pepHit:false, sanctionsHit:false}` |
| `senior-analyst-agent` | Writes `{reviewed:true, recommendation:"PEP entity flagged for enhanced due diligence"}` |
| `sar-drafting-agent` | Reads all findings from input; drafts narrative; calls `complianceReviewLifecycle.openReview()`; writes `{sarNarrative:"...", complianceTaskId:"..."}` |

Worker capability names must match the YAML capability names exactly — this is how the
engine matches bindings to workers.

### `AmlEngineCoordinator`

`@ApplicationScoped`. Injects `AmlInvestigationCaseHub` and `AmlLedgerService`.

Method: `startInvestigation(SuspiciousTransaction) : UUID`.

1. Build initial context `{"transaction": {id, originAccountId, destinationAccountId, amount, currency, timestamp, flagReason}}`
2. Call `caseHub.startCase(context).toCompletableFuture().get(5, SECONDS)` — gets case UUID; investigation continues asynchronously
3. Write `CASE_OPENED` ledger entry (continuity from Layer 4)
4. Return UUID

### `Layer5InvestigationResponse`

Record: `UUID caseId, String status` (always `"started"`).

### `AmlLayer5Resource`

`@Path("/api/layer5/investigations") @ApplicationScoped`.
`POST` accepts `SuspiciousTransaction`, calls `AmlEngineCoordinator.startInvestigation()`,
returns `Layer5InvestigationResponse`.

---

## Dependency Changes (`app/pom.xml`)

| Artifact | Scope | Reason |
|----------|-------|--------|
| `casehub-engine` | compile | CaseHubRuntime, binding evaluation, Quartz |
| `casehub-engine-scheduler-quartz` | compile | `WorkerExecutionManager` via Quartz |
| `casehub-platform-expression` | compile | `JQEvaluator` required by engine CDI (GE-20260523-86ed13) |
| `casehub-platform` | **runtime** (was `test`) | `MockPreferenceProvider @DefaultBean` at augmentation time (PLATFORM.md scope rule) |
| `casehub-engine-persistence-memory` | compile | In-memory `CaseInstanceRepository`, `EventLogRepository`, `CaseMetaModelRepository` |
| `casehub-engine-testing` | test | Test utilities |
| `awaitility` | test | Async assertion for case completion |

---

## `application.properties` Changes

### Test properties (`app/src/test/resources/application.properties`)

Remove: `quarkus.scheduler.enabled=false`

Add:
```properties
# Quartz RAM store (no JDBC tables in tests)
quarkus.scheduler.start-mode=forced
quarkus.quartz.store-type=ram

# GE-20260523-4ca5e7: casehub-work @Scheduled beans use 5-field Unix cron,
# incompatible with Quartz 6-field. Scheduler-only — no WorkItem SLA impact.
# GE-20260428-9311f8: JpaWorkloadProvider conflicts with engine's WorkloadProvider.
quarkus.arc.exclude-types=\
  io.casehub.work.runtime.service.ExpiryLifecycleService,\
  io.casehub.work.runtime.service.ExpiryCleanupJob,\
  io.casehub.work.runtime.service.ClaimDeadlineJob,\
  io.casehub.work.runtime.strategy.RoutingCursorCleanupJob,\
  io.casehub.work.runtime.service.JpaWorkloadProvider

# Jandex: engine modules lack embedded indices; force Quarkus to scan them
quarkus.index-dependency.engine-common.group-id=io.casehub
quarkus.index-dependency.engine-common.artifact-id=casehub-engine-common
quarkus.index-dependency.engine-scheduler-quartz.group-id=io.casehub
quarkus.index-dependency.engine-scheduler-quartz.artifact-id=casehub-engine-scheduler-quartz
quarkus.index-dependency.engine-persistence-memory.group-id=io.casehub
quarkus.index-dependency.engine-persistence-memory.artifact-id=casehub-engine-persistence-memory
```

### Main properties (`app/src/main/resources/application.properties`)

Add:
```properties
# Production: use casehub-work's JpaWorkloadProvider; engine's bridge is redundant
%prod.quarkus.arc.exclude-types=io.casehub.engine.internal.worker.CasehubWorkloadProvider

# Jandex: index engine modules for production augmentation
%prod.quarkus.index-dependency.casehub-engine.group-id=io.casehub
%prod.quarkus.index-dependency.casehub-engine.artifact-id=casehub-engine
%prod.quarkus.index-dependency.casehub-engine-common.group-id=io.casehub
%prod.quarkus.index-dependency.casehub-engine-common.artifact-id=casehub-engine-common
```

---

## Tests

### `AmlInvestigationCaseHubTest` (`@QuarkusTest`)

Structure verification — no async involved:

- `definitionLoads()` — YAML parses without error; namespace `casehub-aml`, name `aml-investigation`
- `hasFiveCapabilities()` — entity-resolution, pattern-analysis, osint-screening, senior-analyst-review, sar-drafting
- `hasFiveBindings()` — same names
- `hasInvestigationCompleteGoal()` — goal named `investigation-complete`, condition contains `complianceTaskId`
- `hasFiveWorkers()` — workers list has 5 entries after `getDefinition()` augmentation

### `AmlLayer5InvestigationTest` (`@QuarkusTest`)

Injects `AmlInvestigationCaseHub caseHub` and `AmlLayer5Resource` indirectly via REST Assured.
All async assertions use Awaitility (5 s timeout, 100 ms poll).

- `startedCaseReturnsId()` — POST normal transaction → 200, non-null `caseId`
- `pepRoutingFiresSeniorAnalyst()` — POST transaction with `flagReason="PEP entity detected"`; Awaitility polls `caseHub.getRuntime().eventLog(caseId)` to verify a `WORKER_SCHEDULED` event for `senior-analyst-review` appears
- `parallelPatternAndOsintFire()` — POST normal transaction; Awaitility verifies both `pattern-analysis` and `osint-screening` worker events appear
- `osintDeclineDoesNotBlockSar()` — Awaitility verifies `sar-drafting` completes despite `osintScreening.declined = true` in context
- `investigationCompletes()` — Awaitility verifies `investigation-complete` goal event appears

---

## Platform Coherence

1. **Already exists?** No engine integration in AML — first.
2. **Right repo?** Application tier consuming orchestration tier. Domain logic stays in AML; engine stays domain-agnostic.
3. **Consolidation?** None — net-new.
4. **Consistent with platform patterns?** `YamlCaseHub` for YAML cases. `casehub-engine-persistence-memory` compile scope. `casehub-platform` at runtime scope. WorkloadProvider disambiguation follows devtown precedent. No SPI reimplementation.
5. **Platform doc update?** `casehub-aml.md` in parent — Layer 5 status update. At epic close.

---

## Known Risks and Mitigations

| Risk | Mitigation |
|------|-----------|
| GE-20260523-fd8725: `when:` at binding level may be silently ignored | Devtown uses `when:` at binding level and passes tests. If it fails, conditions move to `on.contextChange.filter`. Testable. |
| `casehub-platform` scope change from `test` to `runtime` activates additional CDI beans in production augmentation | GE-4ca5e7 fix (exclude scheduler beans) handles any newly activated scheduler beans. Tested in CI. |
| Engine no-op SPI beans (`@ApplicationScoped`) may collide if we later implement any SPI | GE-20260428-9311f8 fix: add per-class excludes. We don't implement any engine SPI now — if we do later, apply the fix at that point. |
