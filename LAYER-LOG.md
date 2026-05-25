# AML Agentic Harness — Layer Log

Structured record of what was built at each layer, optimised for LLM consumption.
Correlates with blog entries in `blog/` and git history. Each entry is the raw
material needed to reproduce the layer in a different domain harness.

---

## Layer 1 — Naive Java (no CaseHub)

**Completed:** 2026-05-10
**Issue:** casehubio/aml#12
**Key files:**
- `api/src/main/java/io/casehub/aml/domain/` — pure domain records: `SuspiciousTransaction`, `InvestigationSummary`, `AmlInvestigationResult`, `EntityResolutionResult`, `PatternAnalysisResult`, `OsintResult`
- `api/src/main/java/io/casehub/aml/investigation/` — specialist service interfaces: `EntityResolutionService`, `PatternAnalysisService`, `OsintScreeningService`, `SarDraftingService`
- `app/src/main/java/io/casehub/aml/tutorial/NaiveAmlInvestigationService.java` — the anti-pattern baseline
- `app/src/main/java/io/casehub/aml/tutorial/Naive*.java` — stub implementations of each specialist service
- `app/src/main/java/io/casehub/aml/AmlInvestigationApplicationService.java` — use-case port interface
- `app/src/main/java/io/casehub/aml/AmlInvestigationResource.java` — REST entry point: `POST /api/investigations`

### What it shows

Direct service calls to specialist services with no accountability, no formal obligation, no SLA, and no audit trail. This is the baseline that every subsequent layer improves. The gap comments in `NaiveAmlInvestigationService` are the teaching mechanism — each one names the specific FinCEN/regulatory requirement that is not met.

### The gap comments

```java
// LAYER 1 GAP: no attribution — who resolved this entity graph?
// No record of which agent made this decision or when.

// LAYER 1 GAP: no failure resilience — if this call times out or throws,
// the entire investigation is lost with no trace of partial work.

// LAYER 1 GAP: no deadline tracking — OSINT runs sequentially after pattern
// analysis. No FinCEN 30-day SLA. No parallel execution. No formal obligation.

// LAYER 1 GAP: no audit trail — this narrative cannot be proven to FinCEN.
// No tamper-evident record of the reasoning chain exists.
```

### Key wiring

**Hexagonal architecture from day one.** `api/` is a pure Java module — no JPA, no Quarkus, no framework dependencies. Domain records and service interfaces live here. `app/` owns use-case orchestration and all framework wiring. This split is mandatory (platform protocol PP-20260512-9b8847).

**`@DefaultBean` on the naive service.** `NaiveAmlInvestigationService` carries `@DefaultBean` so that each subsequent layer can add a `@ApplicationScoped` implementation that takes priority via CDI displacement — without touching the naive code. This is how layers coexist: each new service displaces the previous one at the CDI level.

```java
@ApplicationScoped
@DefaultBean  // displaced by any @ApplicationScoped impl in the same deployment
public class NaiveAmlInvestigationService implements AmlInvestigationApplicationService {
```

**`AmlInvestigationApplicationService`** is the use-case port interface in `app/` (not `api/`) — it takes domain types from `api/` but lives in `app/` because it references the orchestration concern, not pure domain. This placement follows the hexagonal rule.

### Gotchas

- None for Layer 1 — it has no framework dependencies. Any complexity here is a sign that domain logic leaked into infrastructure.

### Pattern to replicate (in another domain)

1. Create `api/` Maven module — pure Java, zero framework imports, zero JPA
2. Define domain records in `api/src/main/java/{package}/domain/` — immutable, no behaviour
3. Define specialist service interfaces in `api/src/main/java/{package}/investigation/` (or equivalent) — one interface per agent concern
4. Create `app/` Maven module — depends on `api/`; owns all Quarkus/CDI wiring
5. Define the use-case port interface in `app/` — takes domain types, returns domain types
6. Implement the naive service with `@ApplicationScoped @DefaultBean` — direct method calls, no CaseHub
7. Add gap comments for every regulatory/compliance requirement not yet met — these are the teaching mechanism
8. Expose `POST /api/{domain-noun}` via a REST resource that injects the port interface
9. Write unit tests for the naive service (no Quarkus needed — plain `new`)

---

## Layer 2 — + casehub-work (compliance officer WorkItem with 30-day FinCEN SLA)

**Completed:** 2026-05-13
**Issue:** casehubio/aml#15
**Key files:**
- `api/src/main/java/io/casehub/aml/domain/AmlInvestigationResult.java` — extended to carry `complianceReviewTaskId`
- `app/src/main/java/io/casehub/aml/tutorial/WorkItemAmlInvestigationService.java` — Layer 2 implementation
- `app/src/test/java/io/casehub/aml/tutorial/WorkItemAmlInvestigationServiceTest.java` — unit test
- `app/src/test/java/io/casehub/aml/AmlInvestigationResourceTest.java` — `@QuarkusTest`
- `app/src/test/resources/application.properties` — qhorus workarounds (see Gotchas)

### What it shows

Adds `casehub-work` to create a formal compliance officer `WorkItem` with a 30-day `claimDeadline` — the FinCEN SAR filing SLA. Closes the "no deadline tracking" gap from Layer 1. The naive investigation still runs (delegated to `NaiveAmlInvestigationService`); the WorkItem is the new accountability layer on top.

`AmlInvestigationResult` now carries a `complianceReviewTaskId` — the caller can use this to track the compliance review independently of the investigation.

### The gap comments addressed

```java
// LAYER 1 GAP: no deadline tracking — OSINT runs sequentially after pattern
// analysis. No FinCEN 30-day SLA. No parallel execution. No formal obligation.
```

Replaced by Layer 2 implementation:

```java
// LAYER 2: create a compliance officer WorkItem with the FinCEN 30-day claim SLA.
// The compliance officer has 30 days from investigation completion to review and file.
WorkItem workItem = workItemService.create(new WorkItemCreateRequest(
        "Compliance review — SAR for transaction " + transaction.id(),
        ...
        "compliance-officers",           // candidateGroups
        Instant.now().plus(30, ChronoUnit.DAYS),  // claimDeadline
        ...
        "aml:investigation/" + transaction.id(),  // callerRef
        ...
));
```

### Key wiring

**`casehub-work-api` in `api/`, `casehub-work` in `app/`.** The api module is JPA-free — `casehub-work-api` contains only the request/response types (no JPA), so it is safe to add to `api/`. The full runtime (`casehub-work`) with JPA entities goes in `app/` only.

**Hibernate scan packages — two packages required.** When adding `casehub-work`, both `io.casehub.work.runtime.model` and `io.casehub.work.runtime.filter` must be declared in the Hibernate scan packages. Omitting `runtime.filter` causes silent failures where filter beans are not found.

```properties
quarkus.hibernate-orm.packages=io.casehub.work.runtime.model,io.casehub.work.runtime.filter
```

**`WorkItemCreateRequest` is a 19-field positional record.** No builder yet (tracked in casehubio/work#168). Pass `null` for unused fields. The key fields: `title`, `category`, `candidateGroups`, `claimDeadline`, `callerRef`.

**CDI displacement pattern.** `WorkItemAmlInvestigationService` is `@ApplicationScoped` without `@DefaultBean` — it displaces `NaiveAmlInvestigationService` at the CDI level. Both classes exist in the build; the one without `@DefaultBean` wins.

```java
@ApplicationScoped  // no @DefaultBean — displaces NaiveAmlInvestigationService
public class WorkItemAmlInvestigationService implements AmlInvestigationApplicationService {
    @Inject NaiveAmlInvestigationService naiveInvestigation;  // delegate for the investigation itself
    @Inject WorkItemService workItemService;
```

### Gotchas

- **Symptom:** Quarkus test startup fails with a Flyway error about duplicate migration version V2.
  **Cause:** `casehub-work` and `casehub-qhorus` both ship a `V2` Flyway migration. When both are on the test classpath, Flyway refuses to start because two migrations share the same version number.
  **Fix:** Disable Flyway in tests and use drop-and-create instead. Do not restore `migrate-at-start=true` until casehubio/qhorus#142 and casehubio/work#162 are resolved. (GE-20260513-74dc72)

  ```properties
  # app/src/test/resources/application.properties
  quarkus.flyway.migrate-at-start=false
  quarkus.flyway.qhorus.migrate-at-start=false
  quarkus.hibernate-orm.database.generation=drop-and-create
  quarkus.hibernate-orm.qhorus.database.generation=drop-and-create
  ```

- **Symptom:** Quarkus test startup fails with a reactive datasource or H2 connection error even though the app uses only JDBC.
  **Cause:** `casehub-qhorus` unconditionally pulls in `quarkus-hibernate-reactive-panache`, which expects a reactive datasource. A JDBC-only consumer has none, causing startup failure.
  **Fix:** Suppress reactive activation in test properties. Do not remove until casehubio/qhorus#141 is resolved. (GE-20260513-4f26a7)

  ```properties
  casehub.qhorus.reactive.enabled=false
  quarkus.datasource.reactive=false
  quarkus.datasource.qhorus.reactive=false
  ```

- **Symptom:** CDI ambiguity error or wrong service injected when both `NaiveAmlInvestigationService` and `WorkItemAmlInvestigationService` are present.
  **Cause:** Both implement `AmlInvestigationApplicationService`. Without `@DefaultBean` on the naive service, CDI sees two equal candidates and fails.
  **Fix:** `@DefaultBean` on the naive service makes it the fallback; any `@ApplicationScoped` without `@DefaultBean` takes priority. This is how layer coexistence works — intentional and reliable, but only if the naive service carries `@DefaultBean`.

### Pattern to replicate (in another domain)

1. Add `casehub-work-api` to `api/pom.xml` — JPA-free, safe in the pure domain module
2. Add `casehub-work` to `app/pom.xml` — brings JPA entities and `WorkItemService`
3. Configure Hibernate scan packages in `application.properties`:
   ```properties
   quarkus.hibernate-orm.packages=io.casehub.work.runtime.model,io.casehub.work.runtime.filter
   ```
4. Add Flyway/reactive workarounds to test `application.properties` if qhorus is on the classpath (copy from AML — tracked as upstream bugs)
5. Extend your result type to carry a `taskId` field
6. Implement a new `@ApplicationScoped` service (no `@DefaultBean`) that:
   - Delegates the domain work to the naive service
   - Calls `WorkItemService.create()` with your domain's SLA as `claimDeadline`
   - Sets `candidateGroups` to your domain's human reviewer group
   - Sets `callerRef` to a URI identifying the domain entity (e.g. `aml:investigation/{id}`)
   - Returns the result with the new `taskId`
7. Write a unit test: verify `WorkItemCreateRequest` fields without Quarkus
8. Write a `@QuarkusTest`: `POST` the domain endpoint, assert the task ID is present in the response and the WorkItem exists in the DB with correct `claimDeadline` and `candidateGroups`
9. Run: `mvn verify -pl api,app -am -Dsurefire.failIfNoSpecifiedTests=false`

---

## Layer 3 — + casehub-qhorus (typed COMMAND/RESPONSE/DONE/DECLINE per specialist agent)

**Completed:** 2026-05-17
**Issue:** casehubio/aml#19
**Epic:** casehubio/aml#9 (Tutorial layers 1–7), epic branch `epic-layer3-qhorus`
**Blog:** 2026-05-16-mdp01-broken-promise-layer-2.md — architectural investigation leading to the composer pattern
**Spec:** workspace `specs/2026-05-17-layer3-composer-qhorus-design.md`
**Key files:**
- `api/src/main/java/io/casehub/aml/domain/SpecialistOutcome.java` — new sealed interface
- `api/src/main/java/io/casehub/aml/domain/InvestigationSummary.java` — three fields now `SpecialistOutcome<T>`
- `api/src/main/java/io/casehub/aml/investigation/SarDraftingService.java` — all three params now `SpecialistOutcome<T>`
- `app/src/main/java/io/casehub/aml/AmlInvestigator.java` — new inner interface (investigation concern)
- `app/src/main/java/io/casehub/aml/ComplianceReviewLifecycle.java` — WorkItem concern extracted from Layer 2
- `app/src/main/java/io/casehub/aml/AmlInvestigationCoordinator.java` — stable outer coordinator
- `app/src/main/java/io/casehub/aml/AmlJacksonConfig.java` — Jackson mixin for sealed interface type discriminator
- `app/src/main/java/io/casehub/aml/agents/` — AgentBehaviour, AgentDispatchMechanism SPIs + three stub behaviours
- `app/src/main/java/io/casehub/aml/tutorial/QhorusAmlInvestigator.java` — Layer 3 investigator
- `app/src/main/java/io/casehub/aml/tutorial/NaiveAmlInvestigationService.java` — now implements AmlInvestigator (not outer port)
- DELETED: `WorkItemAmlInvestigationService.java` — replaced by coordinator + ComplianceReviewLifecycle

### What it shows

Layer 2 had a design flaw: `WorkItemAmlInvestigationService` injected `NaiveAmlInvestigationService` by concrete type, breaking its own commit message's promise that "changing specialist implementations in Layer 3 will propagate automatically." The concrete type injection made CDI displacement impossible.

Layer 3 corrects this with a **composer pattern**: `AmlInvestigationCoordinator` composes an `AmlInvestigator` (swappable via CDI) and `ComplianceReviewLifecycle` (stable WorkItem concern). `QhorusAmlInvestigator` displaces `NaiveAmlInvestigationService` at the inner `AmlInvestigator` level — Layer 2 (WorkItem creation) is transparent to the swap.

Closes the "no formal obligation per specialist agent" gap: each specialist dispatch sends a COMMAND message and receives DONE/DECLINE. `OsintScreeningBehaviour` always DECLINEs ("insufficient clearance for PEP database access") — demonstrating that DECLINE is a formal scope boundary, not an error. The investigation completes and the compliance officer WorkItem is created regardless.

### The gap comments addressed

```java
// LAYER 1 GAP: no attribution — who resolved this entity graph?
// No record of which agent made this decision or when.
// → LAYER 3: COMMAND issued per specialist; DONE/DECLINE persisted in qhorus

// LAYER 1 GAP: no failure resilience — if this call times out or throws,
// the entire investigation is lost with no trace of partial work.
// → LAYER 3: each agent interaction is a formal commitment; FAILURE is an explicit outcome type
```

### Key wiring

**`SpecialistOutcome<T>` in `api/` — pure Java, no Jackson in domain module.**
The sealed interface lives in `api/` (zero external dependencies). Jackson type
discriminator is added via a mixin in `app/src/main/java/io/casehub/aml/AmlJacksonConfig.java`:

```java
@Singleton
public class AmlJacksonConfig implements ObjectMapperCustomizer {
    @JsonTypeInfo(use = Id.NAME, property = "type")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = SpecialistOutcome.Completed.class, name = "Completed"),
        @JsonSubTypes.Type(value = SpecialistOutcome.Declined.class,  name = "Declined"),
        @JsonSubTypes.Type(value = SpecialistOutcome.Failed.class,    name = "Failed")
    })
    interface SpecialistOutcomeMixin {}
    @Override public void customize(ObjectMapper mapper) {
        mapper.addMixIn(SpecialistOutcome.class, SpecialistOutcomeMixin.class);
    }
}
```

Without this, `SpecialistOutcome` fields in JSON responses have no `"type"` discriminator and
REST assertions on `summary.osintScreening.type` return null.

**CDI displacement — concrete type injection was the Layer 2 bug.**
`WorkItemAmlInvestigationService` injected `NaiveAmlInvestigationService` by concrete type.
CDI `@DefaultBean` displacement works at the interface level — injecting by concrete type
prevents any other bean from substituting. Layer 3 fixes this by introducing `AmlInvestigator`
as the injection type. `QhorusAmlInvestigator` (no `@DefaultBean`) displaces
`NaiveAmlInvestigationService` (`@DefaultBean`) automatically.

**Direct dispatch — not ChannelGateway.fanOut().**
`QhorusAmlInvestigator` calls `AgentBehaviour.handle()` directly after sending the COMMAND
message. `channelGateway.fanOut()` does NOT trigger `PushAgentDispatch.post()` as designed —
root cause unknown (#22). The COMMAND and DONE/DECLINE messages ARE persisted to qhorus
via `MessageService.send()`. The formal commitment lifecycle exists in the DB even without
fan-out triggering.

**`casehub.qhorus.reactive.enabled=false` removed — upstream bug resolved.**
The property no longer maps to any config root in the current qhorus version.
Removing it fixes the SmallRye config validation failure at test startup.
Tracked as casehubio/qhorus#141; marked as resolved.

**LedgerVerificationService excluded in tests.**
`LedgerVerificationService` (casehub-ledger) now injects `ReactiveLedgerEntryRepository`
which is vetoed in JDBC-only test mode. Three services excluded from CDI context via
`quarkus.arc.exclude-types` in test `application.properties`. None are exercised by AML tests.

**`@Typed` on `PushAgentDispatch` prevents CDI ambiguity.**
`PushAgentDispatch` implements `AgentChannelBackend`. Without `@Typed`, CDI sees it as a
candidate for `AgentChannelBackend` injection — conflicting with `QhorusChannelBackend`
(the default backend already registered with `ChannelGateway`). Fix:
`@Typed({AgentDispatchMechanism.class, PushAgentDispatch.class})`.

**`WorkItemCreateRequest` now has 23 fields (was 19 at Layer 2, 21 mid-session).**
The record grew by 4 fields since Layer 2. `ComplianceReviewLifecycle` passes null for
all new fields (`templateId`, `permittedOutcomes`, `inputDataSchema`, `outputDataSchema`).
Builder tracked in casehubio/work#168.

**`IllegalStateException` maps to HTTP 409 via casehub-work's exception mapper.**
`IllegalStateExceptionMapper` in casehub-work maps `IllegalStateException` → 409 Conflict.
During implementation, the original poll-timeout exception was typed as `IllegalStateException`
and produced confusing 409 responses. Changed to `RuntimeException`.

### Gotchas

- **Symptom:** Jackson serializes `SpecialistOutcome<T>` fields without a `"type"` discriminator — REST assertions on `summary.osintScreening.type` return null.
  **Cause:** Sealed interfaces don't carry `@JsonTypeInfo` — Jackson doesn't know to add a type field.
  **Fix:** Register a mixin via `ObjectMapperCustomizer` in app/ — keeps api/ pure Java.

- **Symptom:** Tests return HTTP 409 (Conflict) with a 5-second delay instead of 500.
  **Cause:** `casehub-work` ships `IllegalStateExceptionMapper` that maps `IllegalStateException` → 409. A poll-timeout throwing `IllegalStateException` triggered it.
  **Fix:** Use `RuntimeException` for infrastructure failures that shouldn't map to HTTP 409.

- **Symptom:** `@QuarkusTest` startup fails with "SmallRye config validation: casehub.qhorus.reactive.enabled does not map to any root".
  **Cause:** The upstream qhorus bug (unconditional hibernate-reactive activation) was fixed; the config property no longer exists in the current version.
  **Fix:** Remove `casehub.qhorus.reactive.enabled=false` from test `application.properties`.

- **Symptom:** `AmbiguousResolutionException: Ambiguous dependencies for AgentChannelBackend` at CDI startup.
  **Cause:** `PushAgentDispatch` implements `AgentChannelBackend`, making it a CDI candidate alongside `QhorusChannelBackend` (the default). CDI can't choose.
  **Fix:** `@Typed({AgentDispatchMechanism.class, PushAgentDispatch.class})` — restricts CDI visibility to `AgentDispatchMechanism` only.

- **Symptom:** `channelGateway.fanOut()` is called but `PushAgentDispatch.post()` is never invoked.
  **Cause:** Unknown — tracked as casehubio/aml#22. Likely requires specific channel initialization sequence via qhorus internals before `registerBackend()` works.
  **Fix (for tutorial):** Direct dispatch — `QhorusAmlInvestigator` calls `AgentBehaviour.handle()` in-process. COMMAND/DONE/DECLINE messages are still persisted via `MessageService`.

### Pattern to replicate (in another domain)

1. Add `casehub-qhorus` to `app/pom.xml`; qhorus named datasource is already configured
2. Define `SpecialistOutcome<T>` in `api/` — sealed interface with Completed/Declined/Failed records
3. Update domain summary record to use `SpecialistOutcome<T>` for all specialist result fields
4. Update `SarDraftingService`/equivalent to accept `SpecialistOutcome<T>` — pattern-match all three variants
5. Introduce an inner investigator interface in `app/` (e.g. `AmlInvestigator`) — separates investigation from compliance lifecycle
6. Implement the orchestrator investigator (`QhorusXxxInvestigator`, no `@DefaultBean`):
   - Inject `Instance<AgentBehaviour>` to find agents by capability
   - For each specialist: `messageService.send(channelId, ORCHESTRATOR, COMMAND, ...)`, call `behaviour.handle()`, `messageService.send(channelId, capability, DONE/DECLINE, ...)`
   - Return `SpecialistOutcome` for each specialist
7. Implement stub `AgentBehaviour` beans (`@ApplicationScoped @DefaultBean`):
   - Entity/pattern stubs return `Completed` with naive service results
   - Specialised agents (OSINT, PI authorisation) DECLINE with scope reason
8. Register `ObjectMapperCustomizer` in `app/` to add `@JsonTypeInfo` + `@JsonSubTypes` mixin for the sealed interface
9. Exclude `LedgerVerificationService`, `LedgerComplianceReportService`, `LedgerRetentionJob` from test CDI context (add to `quarkus.arc.exclude-types` in test `application.properties`)
10. Add `@Typed({AgentDispatchMechanism.class, YourPushDispatch.class})` to any `AgentChannelBackend` implementation to prevent CDI ambiguity with `QhorusChannelBackend`
11. Test: assert `summary.osintScreening.type` equals `"Declined"` in `@QuarkusTest`; assert `"type": "Completed"` for other specialists

---

## Layer 4 — + casehub-ledger (FinCEN audit trail with AML domain entries)

**Issue:** casehubio/aml#30  
**Spec:** `docs/specs/2026-05-22-message-dispatch-builder-design.md` (qhorus side)  
**Branch closed:** 2026-05-23

### What changed

**New entities and services:**
- `app/src/main/java/io/casehub/aml/ledger/AmlInvestigationLedgerEntry.java` — JPA entity, JOINED inheritance from `LedgerEntry`, `@DiscriminatorValue("AML_INVESTIGATION")`. Fields: `transactionId` (context reference), `eventType` (CASE_OPENED | COMPLIANCE_REVIEW_OPENED)
- `app/src/main/java/io/casehub/aml/ledger/AmlLedgerService.java` — writes CASE_OPENED and COMPLIANCE_REVIEW_OPENED entries. Populates all 8 required base fields. Both entries carry `subjectId = caseId`.
- `app/src/main/resources/db/aml-ledger/migration/V2001__aml_investigation_ledger_entry.sql` — Flyway V2001, `aml_investigation_ledger_entry` join table.

**Interface change:**
- `AmlInvestigator.investigate(SuspiciousTransaction, UUID caseId)` — `caseId` added as second param. All implementations updated. `NaiveAmlInvestigationService` receives and ignores it (Layer 1 gap comment added).

**Result change:**
- `AmlInvestigationResult` gains two new fields: `caseId` (UUID of the investigation case) and `ledgerCaseEntryId` (UUID of the CASE_OPENED entry). Backward-compat 2-arg constructor retained for Layer 1/2.

**qhorus migration:**
- `QhorusAmlInvestigator` migrated from `messageService.send()` to `messageService.dispatch(MessageDispatch.builder()...)` per qhorus#184. Each dispatch carries `subjectId=caseId` (links qhorus MessageLedgerEntry to the AML domain chain) and `inReplyTo=commandResult.messageId()` (correct protocol, was missing before).
- `PushAgentDispatch` migrated to `dispatch()`. Resolves `inReplyTo` via correlationId lookup. Cannot propagate `subjectId` until qhorus#190 (OutboundMessage missing field).

**configuration:**
- Both `application.properties` files: `io.casehub.aml.ledger` added to `quarkus.hibernate-orm.qhorus.packages`; `classpath:db/aml-ledger/migration` added to `quarkus.flyway.qhorus.locations`
- `casehub-platform` added as test dependency for `MockPreferenceProvider` CDI bean (required since work#218)

### Gotchas

- **Symptom:** `@TestTransaction` in `AmlLedgerChainTest` causes "Unable to acquire JDBC Connection" errors. **Cause:** The outer test transaction prevents a second connection being acquired from the pool (H2 in-memory has limited pool). `LedgerWriteService.record()` uses `@Transactional(REQUIRED)` — the issue is that `@TestTransaction` wraps everything and subsequent ledger queries don't see the entries (rolled back). **Fix:** Remove `@TestTransaction`. Use unique transaction IDs per test for isolation.

- **Symptom:** `UnsatisfiedResolutionException: Unsatisfied dependency for type PreferenceProvider`. **Cause:** Updated qhorus/work snapshot requires `casehub-platform` CDI beans (specifically `MockPreferenceProvider`) to be indexed. **Fix:** Add `casehub-platform` as test dependency + `quarkus.index-dependency.casehub-platform.*` to test `application.properties`.

- **Symptom:** qhorus tests pass but `MESSAGE_LEDGER_ENTRY` table not found in AML tests. **Cause:** qhorus tests use `hibernate-orm.database.generation=drop-and-create` (bypasses Flyway); AML tests use `generation=none` (Flyway only). The V2000 migration in the installed qhorus jar was stale. **Fix:** `mvn install` qhorus from source to pick up the correct migration.

### Pattern to replicate (in another domain)

1. Generate a `UUID caseId` in the coordinator at investigation start
2. Create a `XxxLedgerEntry extends LedgerEntry` entity in `app/src/main/java/.../ledger/` — set `@DiscriminatorValue`, `@Table(name = "xxx_ledger_entry")`
3. Create a `XxxLedgerService` with `write*()` methods — populate all 8 base fields (id, subjectId, sequenceNumber, entryType=EVENT, actorId, actorType, actorRole, occurredAt) + your domain fields
4. Add Flyway migration `V2001__xxx_ledger_entry.sql` in `db/xxx-ledger/migration/` (V2001 = first consumer join; V2000 = qhorus join)
5. Update `application.properties` (both main and test): add the package to `quarkus.hibernate-orm.qhorus.packages`; add the migration path to `quarkus.flyway.qhorus.locations`
6. Pass `caseId` through all `messageService.dispatch()` calls as `subjectId`
7. Tests: no `@TestTransaction` when the tested code uses `@Transactional(REQUIRED)` for the ledger write

---

## Layer 5 — + casehub-engine (adaptive investigation paths)

**Issue:** casehubio/aml#31
**Spec:** `docs/specs/2026-05-24-layer5-engine-design.md`
**Completed:** 2026-05-25

### What changed

**New package `io.casehub.aml.engine`:**
- `AmlInvestigationCaseHub extends YamlCaseHub` — loads `aml/aml-investigation.yaml`, augments with 5 in-process worker functions via double-checked locking over `getDefinition()`. Workers are lambdas that capture CDI proxies (`ComplianceReviewLifecycle`, `ObjectMapper`) and delegate to existing stub behaviours.
- `AmlEngineCoordinator` — starts the engine case, writes `CASE_OPENED` ledger entry using the engine-returned case UUID (both identifiers are now the same), returns the UUID.
- `AmlLayer5Resource` — `POST /api/layer5/investigations` returning `Layer5InvestigationResponse { UUID caseId, String status }`.
- `Layer5InvestigationResponse` — response record.

**YAML case definition `app/src/main/resources/aml/aml-investigation.yaml`:**
5 capabilities, 5 `contextChange` bindings:
- `entity-resolution` — fires first (no prior context required)
- `pattern-analysis` — fires after entity (parallel with OSINT)
- `osint-screening` — fires after entity (parallel with pattern)
- `senior-analyst-required` — fires only when `entityType == "PEP"` or `riskScore > 0.8`
- `sar-drafting` — fires when entity + pattern + osint are all non-null; calls `ComplianceReviewLifecycle.openReview()` internally

1 goal: `investigation-complete` when `.complianceTaskId != null`.

**Domain model change:**
- `EntityResolutionResult` gains `entityType` (String) and `riskScore` (double). All callers updated in the same PR. The entity-resolution worker uses `flagReason.contains("PEP")` heuristic to set these fields for tutorial purposes.

**Dependencies added to `app/pom.xml`:**
- `casehub-engine`, `casehub-engine-scheduler-quartz`, `casehub-platform-expression`, `casehub-engine-persistence-memory` (compile), `casehub-engine-testing` (test), `awaitility` (test)
- `casehub-platform` scope changed from `test` to `runtime` — required for production augmentation (`MockPreferenceProvider @DefaultBean` must be visible to the `quarkus:build` goal)
- Versions explicit until parent#65 adds these to BOM dependencyManagement

### What it shows

Layer 4 had a fixed sequential pipeline hardcoded in Java. Layer 5 replaces it entirely with engine binding evaluation. The engine evaluates ALL matching binding conditions simultaneously on every context update:
- **PEP routing** — `senior-analyst-required` fires automatically when the entity-resolution output contains `entityType == "PEP"`. No conditional code in the coordinator.
- **Parallel execution** — `pattern-analysis` and `osint-screening` both have identical preconditions (entity non-null, own result null); the engine fires both simultaneously on the same context change.
- **DECLINE as a first-class outcome** — OSINT always declines, writes `{declined: true, ...}` to context (satisfies the `osintScreening != null` condition), and sar-drafting proceeds without modification.

### The gap comments addressed

```java
// LAYER 1 GAP: no deadline tracking — OSINT runs sequentially after pattern
// analysis. No FinCEN 30-day SLA. No parallel execution. No formal obligation.
// → LAYER 5: engine fires pattern-analysis and osint-screening in parallel;
//             sar-drafting binding waits for both before proceeding
```

### Key wiring

**Worker functions are lambdas in `AmlInvestigationCaseHub`.** Workers are added to the case definition programmatically in `augment()`, which is called once inside a `synchronized(this)` block. The YAML `workers:` section is empty — YAML supplies bindings and capabilities; Java supplies the worker functions. `cap()` helper creates `Capability` objects with passthrough schemas (`.`) because name-matching is all that's needed for binding dispatch; the actual schemas are on the YAML capabilities.

**Engine case UUID is the shared stable identifier.** `AmlEngineCoordinator` starts the case first, receives the engine-generated UUID, then writes the `CASE_OPENED` ledger entry using that UUID. This ensures the engine event log, AML ledger entries, and compliance officer WorkItem all share one identifier.

**GE-20260523-4ca5e7 — Quartz/casehub-work cron format clash.** `casehub-engine-scheduler-quartz` pulls in `quarkus-quartz`. `casehub-work` ships `@Scheduled` beans with 5-field Unix cron; Quartz requires 6-field. Fix in test `application.properties`: `quarkus.scheduler.start-mode=forced`, `quarkus.quartz.store-type=ram`, and `quarkus.arc.exclude-types` for the four casehub-work scheduler beans.

**GE-20260523-86ed13 — engine requires casehub-platform and casehub-platform-expression.** Without `casehub-platform-expression` on the classpath, `JQEvaluator` CDI injection fails and engine beans don't start. Added as compile dep.

**GE-20260428-9311f8 — JpaWorkloadProvider conflicts with engine's internal WorkloadProvider.** In tests: exclude `JpaWorkloadProvider`. In production: exclude `CasehubWorkloadProvider`. Both in `quarkus.arc.exclude-types`.

**Event log metadata key for assertion.** `WORKER_SCHEDULED` events carry `workerName` in metadata. `WORKER_EXECUTION_COMPLETED` events carry `inputDataHash` and `contextChanges` — NOT `workerName`. Integration tests poll `WORKER_SCHEDULED` events, not `WORKER_EXECUTION_COMPLETED`, to detect which workers fired.

### Gotchas

- **Symptom:** `(RECIPIENT_FAILURE,8191) CaseDefinition not found for case: <uuid>` — `SchedulerService.registerScheduledTriggers()` calls `getCaseDefinition()` which returns null.
  **Cause:** `engine-testing` jar not indexed — `TestCaseMetaModelRepository @Priority(1)` not discovered. Without it, `InMemoryCaseMetaModelRepository @Alternative @ApplicationScoped` (no priority) is the only option. In the test run before retry, registration completes, but between retry attempts Quarkus restarts and a timing issue causes the failure on retry.
  **Fix:** Add `quarkus.index-dependency.engine-testing.*` to test `application.properties`. This activates `TestCaseMetaModelRepository @Priority(1)`, `TestCaseInstanceRepository @Priority(1)`, and `TestEventLogRepository @Priority(1)`.

- **Symptom:** Awaitility condition never fires even though workers complete in < 1 second (visible in debug logs).
  **Cause:** `WORKER_EXECUTION_COMPLETED` events don't have `workerName` in metadata — only `inputDataHash` and `contextChanges`. Query filter for `workerName` always returns empty set.
  **Fix:** Poll `WORKER_SCHEDULED` events instead. These always carry `workerName`. A worker being scheduled proves its binding condition was satisfied (all prerequisite workers completed and wrote to context).

- **Symptom:** `casehub-engine` artifacts missing from parent BOM `dependencyManagement`, causing Maven error `'dependencies.dependency.version' is missing`.
  **Fix:** Use explicit `${casehub.version}` in `app/pom.xml` until parent#65 is resolved.

### Pattern to replicate (in another domain)

1. Create `resources/domain/xxx-case.yaml` — define capabilities, bindings (all `contextChange` + JQ `when:` conditions), goals, completion
2. Create `XxxCaseHub extends YamlCaseHub` in a new `engine/` package:
   - `@ApplicationScoped`, `volatile CaseDefinition augmentedDefinition`, double-checked locking
   - Inject CDI beans needed by worker lambdas
   - `augment()` — called once inside `synchronized(this)`; calls `yaml.getWorkers().addAll(workers)`
   - Each worker: `Worker.builder().name(...).capabilities(List.of(cap(name))).function(input -> {...}).build()`
3. Create `XxxEngineCoordinator`:
   - Call `caseHub.startCase(initialContext).toCompletableFuture().get(5, SECONDS)` first
   - Write ledger `CASE_OPENED` entry with the engine-returned UUID
   - Return the UUID
4. Add `engine-testing` index-dependency in test `application.properties`
5. Add GE-20260523-4ca5e7 fix (Quartz/cron exclusions) to test `application.properties`
6. Change `casehub-platform` from `test` to `runtime` scope in `app/pom.xml`
7. Integration tests: poll `WORKER_SCHEDULED` events (not `WORKER_EXECUTION_COMPLETED`) — `WORKER_SCHEDULED` metadata carries `workerName`
8. Assert adaptive paths: for each decision point, start a case with context that triggers the branch, Awaitility-poll for the expected worker to be scheduled
