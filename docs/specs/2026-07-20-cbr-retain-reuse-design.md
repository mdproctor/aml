# CBR Retain + Reuse — Design Spec

**Issues:** #97 (CBR Retain — outcome indexing), #96 (CBR Reuse — path adaptation)
**Epic:** #92 (Case-Based Reasoning)
**Date:** 2026-07-20

## Context

CBR Retrieve (#95) is complete — similar past cases are injected into `CaseContext`
under `cbrExperiences` at case startup via `CbrConfig` with `CASE_LIFETIME` timing.
CBR Retain (#94) stores case profiles on SAR outcome via `AmlCaseProfileStoreObserver`.

Two gaps remain:
1. **Retain** uses `FeatureVectorCbrCase` (flat features, no plan traces) and fires only
   on `SarOutcomeRecordedEvent` — cases without SAR verdicts are never retained, and
   the structured investigation path needed for Reuse is lost.
2. **Reuse** has no mechanism — retrieved experiences sit in context but nothing
   analyses them or feeds recommendations into the investigation flow.

A third gap emerged during brainstorming: the investigation flow has no non-SAR exit
path. Every case routes through SAR drafting regardless of specialist findings. This
biases the CBR case base toward SAR-filed cases, making Reuse recommendations
structurally skewed.

## Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| CBR case type | `PlanCbrCase` directly | Carries `List<PlanTrace>` for structured path data; `FeatureVectorCbrCase` has no value in AML |
| No custom subtype | Use `PlanCbrCase` as-is | Domain semantics encoded in feature keys + outcome strings; avoids deserialization burden |
| Retention trigger | `CaseOutcomeEvent` only | Investigation's conclusion is the CBR outcome; officer verdict is quality feedback for trust routing |
| Outcome model | `TriageDecision` enum | `SAR_WARRANTED`, `FALSE_POSITIVE`, `INCONCLUSIVE` — investigation triage conclusions, not officer verdicts |
| Triage flow | Stub worker, follow-up for real logic | Architectural value is in the flow branching; triage policy is a separate concern |
| Reuse mechanism | Early `cbr-path-advisor` worker | Keeps CBR analysis in app code, clear data structure, no engine SPI changes |
| Advice structure | Capability-oriented statistics | Generic, extensible; new capabilities surface automatically without advisor changes |
| CBR override | Deferred to #110 (workbench) | UX problem, not architecture; context is already mutable |

## §1 — Investigation Triage Flow

A new `investigation-triage` binding fires after all specialist findings, before SAR
drafting. It decides whether a SAR is warranted.

### Capability and binding

```yaml
capabilities:
  - name: investigation-triage
    description: "Evaluate specialist findings and decide whether SAR is warranted"
    inputProjection: "{ entityResolution: .entityResolution, patternAnalysis: .patternAnalysis, osintScreening: .osintScreening, cbrPathAdvice: .cbrPathAdvice }"
    outputProjection: "{ investigationTriage: . }"

bindings:
  - name: investigation-triage
    on: { contextChange: {} }
    when: >-
      .entityResolution != null and
      .patternAnalysis != null and
      .osintScreening != null and
      .investigationTriage == null and
      (.cbrPathAdvice != null or .cbrExperiences == null or (.cbrExperiences | length) == 0)
    capability: investigation-triage
```

The triage binding gates on CBR path advice when experiences exist: if `cbrExperiences`
is non-empty, triage waits for the advisor to complete before firing. When no experiences
exist (empty case base or no CBR config), `cbrPathAdvice` is never written and the
fallback conditions (`cbrExperiences == null` or length 0) allow triage to proceed
immediately.

### Worker output

Writes `investigationTriage` to context:

```json
{
  "decision": "SAR_WARRANTED",
  "reason": "stub — real triage logic pending #112"
}
```

The stub always returns `SAR_WARRANTED`, preserving current behaviour. A follow-up
issue covers real triage logic.

### Conditional branching

The existing `sar-drafting` binding gains a triage gate:

```yaml
- name: sar-drafting
  when: >-
    .entityResolution != null and
    .patternAnalysis != null and
    .osintScreening != null and
    .investigationTriage.decision == "SAR_WARRANTED" and
    .sarNarrative == null
```

### Non-SAR completion

A new goal and completion path:

```yaml
goals:
  - name: investigation-complete
    kind: success
    condition: ".complianceTaskId != null"
  - name: investigation-cleared
    kind: success
    condition: '.investigationTriage.decision == "FALSE_POSITIVE" or .investigationTriage.decision == "INCONCLUSIVE"'

completion:
  success:
    anyOf:
      - investigation-complete
      - investigation-cleared
```

The case completes either when a compliance WorkItem is created (SAR path) or when
triage clears the case (non-SAR path).

### outcomeLabel mapping

The engine sets `CaseOutcomeEvent.outcomeLabel` to the name of the satisfied goal:

| Completion path | Satisfied goal | `outcomeLabel` |
|-----------------|---------------|----------------|
| SAR path | `investigation-complete` | `"investigation-complete"` |
| Cleared path | `investigation-cleared` | `"investigation-cleared"` |

The AML app's `CaseOutcomeObserver` does NOT use `outcomeLabel` — it reads
`investigationTriage.decision` from `caseFileSnapshot` to derive `TriageDecision`.
The `outcomeLabel` is documented here for completeness and for the engine's generic
`CbrCaseRetainObserver` (which is excluded from CDI in this app).

## §2 — Retention Model

### Single observer

Refactor `AmlCaseProfileStoreObserver` to implement `CaseOutcomeObserver` SPI instead
of observing `SarOutcomeRecordedEvent`. Fires on `CaseOutcomeEvent` for ALL completed
cases — both SAR and non-SAR paths.

**CDI discovery:** `AmlCaseProfileStoreObserver` is an `@ApplicationScoped` CDI bean.
Implementing `CaseOutcomeObserver` makes it discoverable via the engine's
`Instance<CaseOutcomeObserver>` injection point — the same mechanism used by the
engine's own `CbrCaseRetainObserver`. The engine iterates all `CaseOutcomeObserver`
instances and calls `onOutcome()` on each.

**Relationship with `CbrCaseRetainObserver`:** The engine's generic observer is
excluded from CDI in this app (via `@IfBuildProfile` or beans.xml exclusion — see
GE-20260720-6ea915). The AML app's observer supersedes it with domain-specific
feature extraction, outcome mapping, and ledger integration. Only one observer
retains CBR cases.

### `TriageDecision` enum

New enum in `api` module:

```java
public enum TriageDecision {
    SAR_WARRANTED,
    FALSE_POSITIVE,
    INCONCLUSIVE
}
```

Named `TriageDecision` to avoid collision with the existing `InvestigationOutcome`
record (which models the compliance gate review decision — APPROVED/REJECTED/UNKNOWN).
Values match the triage worker's `decision` output directly.

Derived from `CaseOutcomeEvent`: read `investigationTriage.decision` from the case file
snapshot. Mapping is identity — enum values equal the triage decision strings.

**Values not included from issue #97 and rationale:**

- `SAR_DECLINED` — under the new model, SAR declining is an officer verdict
  (`SarVerdict`), not an investigation triage conclusion. The investigation triage
  decides whether a SAR is *warranted*; the officer decides whether to *file* it.
  These are architecturally separate concerns — see "Retention trigger" design
  decision and §6 scope boundary.
- `ESCALATED` — not a triage outcome in the current flow. If future triage logic
  (#112) needs an escalation path, it can add this value. The enum is in the `api`
  module and can be extended without breaking existing CBR case data (outcome is
  stored as a String).

### `PlanCbrCase` with `PlanTrace`

The observer queries `PlanItemStore.findByCaseId()` and builds structured traces:

```java
var records = planItemStore.findByCaseId(caseId, tenantId);
var capabilityNameMap = buildRoutingKeyMap(definition);
var traces = records.stream()
    .filter(r -> r.status().isTerminal())
    .filter(r -> capabilityNameMap.containsKey(r.bindingName()))
    .filter(r -> r.executorName() != null)
    .sorted(Comparator.comparing(PlanItemRecord::createdAt))
    .map(r -> new PlanTrace(r.bindingName(),
                            capabilityNameMap.get(r.bindingName()),
                            r.executorName(),
                            OUTCOME_MAP.getOrDefault(r.status(), r.status().name()),
                            index, Map.of()))
    .toList();

var cbrCase = new PlanCbrCase(problem, solution,
    triageDecision.name(), null, features, traces);
```

The trace-building logic mirrors the engine's `CbrCaseRetainObserver` pattern:
filter to terminal plan items with known bindings and executor names, sort by
creation time, and map `TaskStatus` to outcome strings via `OUTCOME_MAP`.

### Ledger entry

`AmlCaseProfileLedgerEntry.outcome` stores `TriageDecision.name()` instead of
`SarVerdict.name()`.

The `confidence` field becomes nullable to reflect that no officer accuracy score
exists at case completion time. This requires:

1. Type change: `double` → `Double` (primitive to boxed)
2. Annotation change: `@Column(name = "confidence", nullable = true)`
3. Flyway migration: `ALTER TABLE aml_case_profile_ledger_entry ALTER COLUMN confidence DROP NOT NULL`
4. Update `domainContentBytes()`: use `confidence != null ? String.valueOf(confidence) : ""` instead of `String.valueOf(confidence)`
5. Verify no code assumes non-null primitive semantics for this field

### CbrConfig changes

`CbrConfig.cbrType` changes from `"feature-vector"` to `"plan"` to match
`PlanCbrCase.CBR_TYPE`.

### Migration: old `FeatureVectorCbrCase` entries

After the `cbrType` change, the `CbrRetrievalService` resolves `caseClass` to
`PlanCbrCase.class` for retrieval. The store behaviour for old entries depends on
the `cbrType` column:

- **If the store filters by `cbrType` internally:** old `FeatureVectorCbrCase`
  entries (stored with `cbrType="feature-vector"`) are silently excluded from
  retrieval results. New cases start with a clean case base that grows as
  `PlanCbrCase` entries are retained. This is the expected and desirable behaviour.

- **If the store does NOT filter by `cbrType`:** old entries would be retrieved
  and deserialized as `PlanCbrCase`. Since `FeatureVectorCbrCase` lacks `planTrace`,
  deserialization may produce entries with empty plan traces. The advisor handles
  this gracefully (inner loop produces no statistics), but outcome labels differ:
  old entries use `SarVerdict` values (`UPHELD`, `WITHDRAWN`, `FLAGGED`) while
  new entries use `TriageDecision` values (`SAR_WARRANTED`, `FALSE_POSITIVE`,
  `INCONCLUSIVE`). Mixed outcome labels would produce confusing advisor statistics.

**Mitigation:** The `CbrCaseMemoryStore` implementation stores `cbrType` as a column
and the `retrieveSimilar` implementation should filter by the requested class's
`cbrType`. If verification shows this filtering is absent, a one-time data migration
or a `CbrQuery` filter by `cbrType` is needed (engine change — separate issue).

**Testing:** §5 adds a backward-compatibility test for the advisor against old-format
entries to verify the actual store behaviour.

### SarOutcomeRecordedEvent observer

No longer does CBR retention. The event still fires (officer verdict), and
`SarOutcomeFeedbackService` still observes it for trust scoring. CBR retention
responsibility moves entirely to the `CaseOutcomeObserver`.

## §3 — CBR Path Advisor (Reuse)

### New capability and binding

```yaml
capabilities:
  - name: cbr-path-advisor
    description: "Analyse similar past cases and produce path recommendations"
    inputProjection: "{ cbrExperiences: .cbrExperiences }"
    outputProjection: "{ cbrPathAdvice: . }"

bindings:
  - name: cbr-path-advisor
    on: { contextChange: {} }
    when: ".cbrExperiences != null and (.cbrExperiences | length) > 0 and .cbrPathAdvice == null"
    capability: cbr-path-advisor
```

### Worker logic

The worker receives `cbrExperiences` as `List<Map<String, Object>>` — serialized
`RetrievedExperience` records injected by the engine's `CaseStartedEventHandler`.
Each map contains fields: `problem`, `solution`, `outcome`, `confidence`,
`similarityScore`, `features`, `planTrace` (list of `ExperiencePlanStep` maps),
and `featureSimilarities`.

```java
@SuppressWarnings("unchecked")
List<Map<String, Object>> experiences = (List<Map<String, Object>>) input.get("cbrExperiences");

for (Map<String, Object> experience : experiences) {
    double score = ((Number) experience.get("similarityScore")).doubleValue();
    String outcome = (String) experience.get("outcome");
    List<Map<String, Object>> planTrace = (List<Map<String, Object>>) experience.get("planTrace");

    if (planTrace != null) {
        for (Map<String, Object> step : planTrace) {
            String capabilityName = (String) step.get("capabilityName");
            String stepOutcome = (String) step.get("stepOutcome");
            stats.computeIfAbsent(capabilityName, CapabilityStats::new)
                 .record(stepOutcome, score);
        }
    }
    outcomeStats.record(outcome);
}
```

### Output structure

`cbrPathAdvice` written to context:

```json
{
  "caseCount": 8,
  "minSimilarity": 0.72,
  "avgSimilarity": 0.81,
  "capabilities": {
    "senior-analyst-review": {
      "frequency": 0.75,
      "outcomes": { "SUCCESS": 5, "DECLINED": 1 }
    },
    "pattern-analysis": {
      "frequency": 1.0,
      "outcomes": { "SUCCESS": 8 }
    }
  },
  "predominantOutcome": "SAR_WARRANTED",
  "predominantOutcomeFrequency": 0.875,
  "confidence": 0.82
}
```

### Confidence formula

`avgSimilarity × min(1.0, caseCount / 5.0)` — cold-start penalty that discounts
confidence when fewer than 5 similar cases exist. At 5+ cases, confidence equals
`avgSimilarity`. This is intentional: case count beyond 5 provides diminishing
marginal information — the quality of matching (similarity) matters more than the
quantity of matches.

### Graceful degradation

If `cbrExperiences` is empty or absent, the binding doesn't fire. Downstream bindings
referencing `.cbrPathAdvice` treat its absence as "no CBR input" — existing conditions
still work without it.

### Failure handling

The advisor worker catches all exceptions and writes a fallback result on failure:

```json
{
  "caseCount": 0,
  "error": true,
  "errorReason": "advisor failed — proceeding without CBR advice"
}
```

This follows the established pattern (OSINT screening writes
`osintScreening.declined=true` on failure so `sar-drafting` isn't blocked). Without
fallback output, a worker failure leaves `cbrPathAdvice` null while `cbrExperiences`
is non-empty — the triage binding's condition (§1) would never be satisfied and the
investigation would be permanently stuck.

### Ledger entry for CBR advice

When the advisor writes `cbrPathAdvice` to context, it also writes an
`AmlCbrAdvisoryLedgerEntry` recording:

- `caseCount`, `avgSimilarity`, `confidence` from the advice
- `predominantOutcome` and `predominantOutcomeFrequency`
- Which capabilities were recommended (frequency > 0.5)

The entity extends `JpaLedgerEntry` (joined inheritance) with
`@DiscriminatorValue("AML_CBR_ADVISORY")`. Requires a Flyway migration to create
the `aml_cbr_advisory_ledger_entry` table for its specific columns.

This creates an audit trail for CBR-influenced routing decisions. When a downstream
binding fires due to CBR advice (e.g., `senior-analyst-required-resolution` triggered
by CBR frequency > 0.6), the ledger entry shows what CBR data informed the decision.

## §4 — Binding Changes

Only two bindings change.

### `senior-analyst-required-resolution`

Adds CBR as an alternative trigger:

```yaml
- name: senior-analyst-required-resolution
  on: { contextChange: {} }
  when: >-
    .entityResolution != null and
    .priorEntityContext.knownHighRisk != true and
    (.entityResolution.entityType == "PEP" or
     .entityResolution.riskScore > 0.8 or
     (.cbrPathAdvice != null and
      .cbrPathAdvice.capabilities["senior-analyst-review"].frequency > 0.6)) and
    .seniorAnalystReview == null
  capability: senior-analyst-review
```

### Design property

CBR advice is always additive — it can trigger a capability that wouldn't have fired
otherwise, but never suppresses a capability that existing conditions would trigger.

### Unchanged bindings

- `entity-resolution` — fires on transaction presence, no CBR input
- `pattern-analysis`, `osint-screening` — fire after entity resolution, no CBR input
- `senior-analyst-required-prior-context` — prior context, independent of CBR
- `sar-drafting` — gated on triage decision (§1), no CBR input
- `compliance-review-opening` — fires on SAR narrative, no CBR input

## §5 — Testing

### Unit tests (api module)

- `TriageDecision`: mapping from triage decision strings
- Confidence formula edge cases (zero cases, single case, high similarity)

### @QuarkusTest integration (app module)

**Retain — SAR path:** start investigation → specialists → triage (SAR_WARRANTED) →
sar-drafting → compliance-review → case completes → assert `PlanCbrCase` in
`CbrCaseMemoryStore` with correct `PlanTrace` entries and outcome `SAR_WARRANTED` →
assert `AmlCaseProfileLedgerEntry` written.

**Retain — non-SAR path:** start investigation → specialists → triage
(FALSE_POSITIVE via test context injection) → case completes on
`investigation-cleared` goal → assert `PlanCbrCase` with outcome `FALSE_POSITIVE` →
assert ledger entry written.

**Reuse — advisor fires:** pre-populate `CbrCaseMemoryStore` with known cases →
start investigation → assert `cbrPathAdvice` in context with correct capability stats.

**Reuse — advisor doesn't fire:** empty case base → assert `cbrPathAdvice` absent →
investigation completes normally.

**Reuse — CBR-influenced routing:** pre-populate cases where 80% used senior-analyst →
start case that wouldn't trigger senior-analyst by risk score → assert senior-analyst
IS dispatched due to CBR advice.

**Reuse — advisor audit trail:** pre-populate cases → start investigation → assert
`AmlCbrAdvisoryLedgerEntry` written with correct CBR statistics.

**Cold start:** empty case base → all bindings work exactly as before.

**Backward compatibility — old FeatureVectorCbrCase entries:** pre-populate store with
`FeatureVectorCbrCase` entries (old format) → start investigation → verify advisor
handles them gracefully (empty plan traces, different outcome labels).

**Triage waits for CBR advice:** pre-populate case base → start investigation →
verify triage does NOT fire until `cbrPathAdvice` is written → verify triage
receives advice in its input projection.

**Advisor failure fallback:** inject malformed experience data (e.g., missing
`planTrace` key causing `NullPointerException` in advisor loop) → verify
`cbrPathAdvice` is written with `error: true` and `caseCount: 0` → verify
triage fires and investigation completes normally. This tests the liveness
guarantee from §3 "Failure handling."

### Test conventions

- Drain to `status=completed` before assertions
- `casehub.ledger.hash-chain.enabled=false`
- Ledger subject isolation: `UUID.nameUUIDFromBytes("aml-<concern>:" + caseId)`
- `CbrCaseRetainObserver` excluded from CDI
- Gate approval ordering for PlannedAction workers
- `CbrQuery.withNotBefore(Instant.now())` for test isolation (GE-20260716-986cd1)

## §6 — Scope Boundaries

### Follow-up issues

1. **Investigation triage logic** (#112) — replace SAR_WARRANTED stub with real decision logic
2. **CBR override mechanism** — deferred to #110 (workbench)
3. **CBR outcome refinement on officer verdict** (wsp-casehub-aml#2) — the retained
   CBR case records the investigation's triage decision (`SAR_WARRANTED`), not the
   officer's subsequent verdict. If the officer declines the SAR, the CBR case still
   says `SAR_WARRANTED` — which is accurate (the investigation *did* warrant a SAR),
   but doesn't reflect the full lifecycle. A future enhancement could use
   `CbrCaseMemoryStore.recordOutcome()` to update the CBR case when the officer
   verdict arrives, adding a composite outcome that distinguishes "SAR warranted and
   filed" from "SAR warranted but declined." This is architecturally separate from
   trust scoring (which already processes officer verdicts) and requires careful
   design of the composite outcome model.

### Out of scope

- Officer verdict feedback loop — stays in trust routing, unchanged
- `FeatureVectorCbrCase` cleanup in neocortex — platform type, other apps may use it
- Cross-encoder reranking — current feature-only retrieval sufficient

### Cross-repo impact

None. All types used (`PlanCbrCase`, `PlanTrace`, `CaseOutcomeObserver`,
`CaseOutcomeEvent`) are existing engine/neocortex API surface.

### Files touched

| File | Change |
|------|--------|
| `AmlCaseProfileStoreObserver` | Refactor: `CaseOutcomeObserver` SPI, `PlanCbrCase` output |
| `AmlCaseProfileLedgerEntry` | Update: outcome field semantics, `confidence` nullable (`double` → `Double`) |
| `AmlInvestigationCaseHub` | Update: `cbrType("plan")`, register triage + advisor capabilities |
| `AmlCbrSchema` | Update: `CASE_TYPE` alignment if needed |
| `aml-investigation.yaml` | Add: triage + advisor bindings, cleared goal; modify sar-drafting condition |
| `AmlInvestigationCaseDescriptor` | Add: triage + advisor worker registrations |
| `TriageDecision` (new) | Enum in api module |
| `CbrPathAdvisorWorker` (new) | Worker in app/cbr |
| `InvestigationTriageWorker` (new) | Stub worker in app/cbr |
| `AmlCbrAdvisoryLedgerEntry` (new) | Ledger entry for CBR advice audit trail (`@DiscriminatorValue("AML_CBR_ADVISORY")`) |
| Flyway migration V*__confidence_nullable (new) | `ALTER TABLE aml_case_profile_ledger_entry ALTER COLUMN confidence DROP NOT NULL` |
| Flyway migration V*__cbr_advisory_ledger (new) | `CREATE TABLE aml_cbr_advisory_ledger_entry` — joined inheritance table for `AmlCbrAdvisoryLedgerEntry` columns (`case_count`, `avg_similarity`, `confidence`, `predominant_outcome`, `predominant_outcome_frequency`, `recommended_capabilities`) |
| Tests (new + modified) | Per §5 |

## Garden Entries Referenced

- GE-20260720-6ea915 — CbrCaseRetainObserver coupling gotcha (CDI exclusion)
- GE-20260612-bd3b4d — Degenerate CBR diagnosis (Retain+Reuse gap)
- GE-20260716-986cd1 — InMemoryCbrCaseMemoryStore test isolation
- GE-20260717-0489d1 — CbrQuery/store Path scope parameter
- GE-20260718-95e11e — store() 6th param caseType not scope
