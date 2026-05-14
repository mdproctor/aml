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

- **Flyway V2 conflict between casehub-work and casehub-qhorus.** Both ship a V2 Flyway migration. If both are on the classpath in tests, Flyway refuses to start. Workaround: disable Flyway in tests and use drop-and-create. Do not remove until casehubio/qhorus#142 and casehubio/work#162 are resolved. (GE-20260513-74dc72)

  ```properties
  # app/src/test/resources/application.properties
  quarkus.flyway.migrate-at-start=false
  quarkus.flyway.qhorus.migrate-at-start=false
  quarkus.hibernate-orm.database.generation=drop-and-create
  quarkus.hibernate-orm.qhorus.database.generation=drop-and-create
  ```

- **qhorus activates Hibernate Reactive unconditionally.** Any non-reactive consumer must suppress it in test properties. Do not remove until casehubio/qhorus#141 is resolved. (GE-20260513-4f26a7)

  ```properties
  casehub.qhorus.reactive.enabled=false
  quarkus.datasource.reactive=false
  quarkus.datasource.qhorus.reactive=false
  ```

- **`@DefaultBean` displacement works across modules.** The naive service in one CDI bean and the work-item service in another — CDI sees both, the one without `@DefaultBean` wins. This is intentional and reliable, but surprising if you expect only one implementation to exist.

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
