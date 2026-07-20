# CBR Retrieve Design — #95

## Summary

Configure the engine's `CbrConfig` on the AML investigation case definition so that similar past investigations are retrieved at case startup and injected into `CaseContext` as advisory context for bindings and workers.

## Context

Issue #95 is the third issue in the CBR epic (#92). It depends on #93 (case similarity model) and #94 (case profile store). It delivers the CBR **Retrieve** step — finding similar past investigations when a new case starts.

### What Already Exists

| Component | Location | What it provides |
|-----------|----------|------------------|
| `CaseProfile` | `api/.../domain/` | 6-dimension record with `toFeatures()` → `Map<String, FeatureValue>` |
| `AmlCbrSchema` | `app/.../cbr/` | `CbrFeatureSchema` with 7 fields (6 dimensions + sar_narrative), similarity specs, weights. Registered at startup |
| `AmlCaseProfileStoreObserver` | `app/.../cbr/` | Stores completed investigation profiles as `FeatureVectorCbrCase` on SAR outcome (CBR Retain) |
| `AmlCbrSchemaRegistrar` | `app/.../cbr/` | Registers `AmlCbrSchema.SCHEMA` with `CbrCaseMemoryStore` at startup |
| `CbrCaseMemoryStore` | neocortex `memory-api` | Platform SPI: `store()`, `retrieveSimilar()`, `recordOutcome()` |
| `CbrConfig` | engine-api | Declarative CBR configuration: JQ feature extraction, topK, minSimilarity, weights, timing, domain, caseType |
| `CbrRetrievalService` | engine runtime | Orchestration: extract features → `CbrQuery` → `retrieveSimilar()` → `RetrievedExperience` mapping, with CASE_LIFETIME caching and failure recovery |
| `RetrievedExperience` | engine-api | Result record: problem, solution, outcome, confidence, similarityScore, features, planTrace, featureSimilarities |
| `CaseStartedEventHandler` | engine runtime | Wired in engine#761 — calls `CbrRetrievalService.retrieve()` after RUNNING transition, injects `List<Map>` into `CaseContext` working layer under `"cbrExperiences"` via `engineSet()` |

### Platform Coherence

- **Boundary rule**: "Do not implement CBR retrieval logic in application repos. Application repos provide domain-specific feature vectors and similarity thresholds." This design complies — AML provides JQ feature expressions and weights via `CbrConfig`; the engine handles retrieval.
- **CBR architecture** (platform/cbr.md §1): Retrieve = `CbrRetrievalService` (engine) + `CbrCaseMemoryStore` (neocortex). This design uses the engine's retrieve mechanism via `CbrConfig`.
- **Protocol**: `aml-ledger-entry-tenancy-id-non-null` — no new ledger entries in this issue.
- **Retain isolation**: `CbrCaseRetainObserver` (engine) fires on `CaseOutcomeEvent` when `CbrConfig` is present. AML has custom retain via `AmlCaseProfileStoreObserver`. Exclude the platform observer to prevent duplicate entries.

### Garden Entries

- **GE-20260718-95e11e**: `CbrCaseMemoryStore.store()` 6th parameter is `caseType` not scope — decompiled interface loses names. Not directly relevant to retrieve (store is already correct from #94), but documents the API contract.
- **GE-20260716-986cd1**: `InMemoryCbrCaseMemoryStore` retains cases across `@QuarkusTest` methods — use `withNotBefore()` for test isolation.
- **GE-20260717-0489d1**: `CbrQuery.of()` gained mandatory `Path scope` parameter; `Path` collision with `jakarta.ws.rs.Path`. Not relevant here — `CbrRetrievalService` builds the query internally.

## Design

### 1. Add CbrConfig to AmlInvestigationCaseHub

**Location:** `app/src/main/java/io/casehub/aml/engine/AmlInvestigationCaseHub.java`

In the `augment(CaseDefinition)` method, after adding workers:

```java
definition.setCbrConfig(CbrConfig.builder()
    .feature("flag_reason", ".transaction.flagReason")
    .feature("transaction_amount", ".transaction.amount")
    .feature("prior_incident_count", ".priorEntityContext.entityRiskCount")
    .feature("entity_type", ".entityResolution.entityType")
    .domain("aml.cbr")
    .caseType(AmlCbrSchema.CASE_TYPE)
    .topK(10)
    .minSimilarity(0.5)
    .vectorWeight(0.0)
    .timing(CbrRetrievalTiming.CASE_LIFETIME)
    .cbrType("feature-vector")
    .weight("flag_reason", 0.30)
    .weight("transaction_amount", 0.15)
    .weight("prior_incident_count", 0.10)
    .weight("entity_type", 0.20)
    .build());
```

**4 features declared; 3 available at startup.** JQ expressions against absent context keys return null; `CbrRetrievalService` skips null features. With `CASE_LIFETIME`, retrieval fires once at case start — only `flag_reason` (30%), `transaction_amount` (15%), and `prior_incident_count` (10%) are present. `entity_type` (20%) is declared for forward compatibility with `PER_EVALUATION` timing — it becomes available after entity-resolution runs. The scorer normalises by the weight of present features (`weightedSum / totalWeight`), so 3 features produce a full [0, 1] similarity range. The tradeoff: with only 3 of 4 weighted dimensions, cases that differ on entity type appear identical if they match on the other 3 features. With `topK(10)`, retrieved cases may span different investigation profiles that happen to share the same flag reason, similar amounts, and comparable prior history.

**Features intentionally excluded from CbrConfig:**
- `jurisdiction_risk`, `network_complexity` — no worker currently outputs these fields. The osint-screening worker produces `{declined, reason, pepHit, sanctionsHit}` and the pattern-analysis worker produces `{structuringDetected, description}`. Adding these features requires worker enhancements (wsp-casehub-aml#1), not just a timing change to `PER_EVALUATION`.
- `sar_narrative` — declared in `AmlCbrSchema` for store-side schema completeness, but excluded from CbrConfig because (a) no data path at `CASE_LIFETIME`, (b) `CbrSimilarityScorer` handles `Text` fields with exact string match — useless for narrative comparison, and (c) the builder's default weight of 1.0 for undeclared features would make it dominate scoring if it ever became active.

**Weights are a subset of `AmlCbrSchema.WEIGHTS`** — identical values for the 4 declared features. The schema retains all 6 weights for store-side similarity scoring across the full feature space.

**`vectorWeight(0.0)`** — explicit feature-only scoring. No problem text is set on the query, and the in-memory store has no embedding model. Setting 0.0 documents the intent and prevents silent 50% vector blending if a production store supports embeddings.

**`CASE_LIFETIME` timing** — retrieve once at startup, cache for the case. Results are advisory context, not prescriptive. Re-retrieving after enrichment would change the similar cases mid-investigation, which is confusing for workers that already consumed the initial set.

**`cbrType("feature-vector")`** — matches `FeatureVectorCbrCase.CBR_TYPE`, which is what `AmlCaseProfileStoreObserver` stores.

### 2. Exclude CbrCaseRetainObserver

**Location:** Both `application.properties` files (main and test)

```properties
quarkus.arc.exclude-types=...,io.casehub.engine.internal.memory.CbrCaseRetainObserver
```

**Why both:** `quarkus:build` validates CDI against the main classpath. Test-only exclusion would fail the production build.

**Why exclude:** `CbrCaseRetainObserver` fires on `CaseOutcomeEvent` and stores a `PlanCbrCase` when `CbrConfig` is present. AML already stores an `AmlCaseProfileStoreObserver`-driven `FeatureVectorCbrCase` with domain-specific feature extraction (CaseProfile dimensions), SAR-specific outcome labels, and a compliance ledger entry. The platform observer would create a second entry with generic JQ-extracted features — different feature values for the same case, degrading retrieval quality.

### 3. Rebuild Engine SNAPSHOT

Pick up engine commit `f666c6b4` (feat(#761)) so `CaseStartedEventHandler` has the CBR wiring.

### What AML Does NOT Need

| Rejected | Why |
|----------|-----|
| `AmlCbrRetrievalService` (app-specific retrieval wrapper) | Engine handles retrieval via `CbrConfig` |
| `SimilarCase` domain record | `RetrievedExperience` (engine-api) carries all needed fields |
| Coordinator changes (`AmlEngineCoordinator`) | Retrieval happens inside engine lifecycle, not in coordinator |
| YAML binding changes | Results are advisory under `.cbrExperiences` — no binding conditions reference them yet |

### Result Shape in CaseContext

After case startup, the working layer contains:

```json
{
  "cbrExperiences": [
    {
      "problem": "Flagged transaction TX-001: STRUCTURING ...",
      "solution": "entity-resolution → pattern-analysis → osint-screening → sar-drafting → compliance-review-opening",
      "outcome": "UPHELD",
      "confidence": 0.87,
      "similarityScore": 0.82,
      "features": { "flag_reason": "STRUCTURING", "transaction_amount": 50000.0, ... },
      "featureSimilarities": { "flag_reason": 1.0, "transaction_amount": 0.75, ... },
      "planTrace": []
    }
  ]
}
```

Bindings can reference `.cbrExperiences` in JQ conditions. Worker input projections can include `"{ similarCases: .cbrExperiences }"`. Both are deferred to #96 (CBR Reuse).

## Testing

### Unit Tests

| Test | Scope |
|------|-------|
| `AmlInvestigationCaseHubTest` | Verify `CbrConfig` is set on the case definition with correct features, weights, domain, caseType |

### @QuarkusTest

| Test | Scope |
|------|-------|
| CBR retrieve integration | Store a case profile (fire `SarOutcomeRecordedEvent`), start a new investigation with a similar transaction → verify `.cbrExperiences` in case context contains the stored case with matching features |
| Empty case base | Start investigation with no prior CBR cases → `.cbrExperiences` absent from context (not empty list) |
| Feature subsetting | Store a complete 6-dimension profile (UPHELD), start investigation with matching 3-dimension initial profile → verify match on available features with reasonable similarity score |
| Retain isolation | Start and complete investigation → verify only `AmlCaseProfileStoreObserver` creates a CBR entry (no duplicate from `CbrCaseRetainObserver`) |
| Test isolation | `withNotBefore()` pattern (GE-20260716-986cd1) to isolate CBR queries across test methods |

Test conventions per CLAUDE.md: hash chain disabled, drain engine to completion, ledger subject isolation, `JpaActorTrustScoreRepository` selected, `qhorus-persistence-memory` excluded.

## Scope Boundary

**In scope:** CBR Retrieve — configure `CbrConfig` so similar past investigations are retrieved at case startup.

**Out of scope (later issues):**
- #96 — CBR Reuse (investigation path adaptation from similar cases — would add binding conditions referencing `.cbrExperiences`)
- #97 — CBR Retain outcome update (`CbrCaseMemoryStore.recordOutcome()`)
- #98 — SAR narrative seeding from similar past cases
- #99 — Cold-start case base seeding
- wsp-casehub-aml#1 — Worker enhancements for `jurisdiction_risk` and `network_complexity` CBR features
