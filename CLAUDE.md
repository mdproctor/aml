# aml Workspace

**Project repo:** /Users/mdproctor/claude/casehub/aml
**Workspace type:** public

## Session Start

Run `add-dir /Users/mdproctor/claude/casehub/aml` before any other work.

## Artifact Locations

| Skill | Writes to |
|-------|-----------|
| brainstorming (specs) | `specs/` |
| writing-plans (plans) | `plans/` |
| handover | `HANDOFF.md` |
| idea-log | `IDEAS.md` |
| design-snapshot | `snapshots/` |
| java-update-design / update-primary-doc | `design/JOURNAL.md` (created by `epic`) |
| adr | `adr/` |
| write-blog | `blog/` |

## Structure

- `HANDOFF.md` — session handover (single file, overwritten each session)
- `IDEAS.md` — idea log (single file)
- `specs/` — brainstorming / design specs (superpowers output)
- `plans/` — implementation plans (superpowers output)
- `snapshots/` — design snapshots with INDEX.md (auto-pruned, max 10)
- `adr/` — architecture decision records with INDEX.md
- `blog/` — project diary entries with INDEX.md
- `design/` — epic journal (created by `epic` at branch start)

## Rules

- All methodology artifacts go here, not in the project repo
- Promotion to project repo is always explicit — never automatic
- Workspace branches mirror project branches — switch both together

## Routing

| Artifact   | Destination | Notes |
|------------|-------------|-------|
| adr        | workspace   | |
| blog       | workspace   | |
| design     | workspace   | |
| snapshots  | workspace   | |
| specs      | workspace   | |
| handover   | workspace   | |

---

# casehub-aml — Claude Code Project Guide

## Platform Context

This repo is one component of the casehubio multi-repo platform. **Before implementing anything — any feature, SPI, data model, or abstraction — run the Platform Coherence Protocol.**

> **Platform docs:** Paths below are local (use `Read`). If the path does not exist — standalone clone on another machine — replace `/Users/mdproctor/claude/casehub/parent/docs/` with `https://raw.githubusercontent.com/casehubio/parent/main/docs/` and use `WebFetch`.

The protocol asks: Does this already exist elsewhere? Is this the right repo for it? Does this create a consolidation opportunity? Is this consistent with how the platform handles the same concern in other repos?

**Platform architecture (fetch before any implementation decision):**
```
/Users/mdproctor/claude/casehub/parent/docs/PLATFORM.md
```

**Foundation repo deep-dives:**
- casehub-engine: `/Users/mdproctor/claude/casehub/parent/docs/repos/casehub-engine.md`
- casehub-ledger: `/Users/mdproctor/claude/casehub/parent/docs/repos/casehub-ledger.md`
- casehub-work: `/Users/mdproctor/claude/casehub/parent/docs/repos/casehub-work.md`
- casehub-qhorus: `/Users/mdproctor/claude/casehub/parent/docs/repos/casehub-qhorus.md`
- casehub-connectors: `/Users/mdproctor/claude/casehub/parent/docs/repos/casehub-connectors.md`

---

## Project Type

type: java

**Stack:** Java 21 (on Java 26 JVM), Quarkus 3.32.2, GraalVM 25 (native image target)

---

## What This Project Is

`casehub-aml` is the **Anti-Money Laundering investigation application** built on the CaseHub platform foundation. It is the primary community tutorial for Java/Quarkus developers — demonstrating all CaseHub capabilities in a domain every Java enterprise developer recognises from their own work in banking infrastructure.

This is an **application layer**, not a framework. The foundation (casehub-engine, casehub-qhorus, casehub-ledger, casehub-work, casehub-connectors) provides coordination, accountability, audit, and compliance primitives. casehub-aml provides the financial crime investigation domain logic on top: what a suspicious transaction is, how an AML investigation proceeds, which specialists handle what, and how a SAR reaches a compliance officer.

### Why AML

Java dominates banking and financial services infrastructure. Enterprise Java developers at any major financial institution have built or integrated transaction monitoring, case management, and compliance reporting systems. They recognise the failure modes first-hand: audit trails that cannot reconstruct the decision chain, human escalation that fires too late, and SAR filings where nobody can say which agent made the call.

The specific compliance gap current agentic AML systems cannot close — scored 44/50 in the use-case analysis at `docs/use-case-analysis.md` in casehub-parent:

| FinCEN/FATF requirement | Current agentic AML | casehub-aml |
|---|---|---|
| Auditable evidence chains — who recommended what and why | Append-only logs inconsistent; no decision attribution | Commitment per agent task; `causedByEntryId` chains the full investigation |
| Human sign-off on SAR filing with 30-day SLA | Ad-hoc escalation; no formal deadline | WorkItem with `claimDeadline`; auto-escalation to head of compliance |
| GDPR on transaction data and PII | Not addressed | `LedgerErasureService` + `DecisionContextSanitiser` |
| Tamper-evident investigation record | No cryptographic audit | Merkle inclusion proofs; independently verifiable |
| Trust-weighted routing — experienced analysts on complex cases | No trust model | Bayesian Beta from SAR outcome attestations |

**Comparison baseline:** IBM AMLSim (open source, simulation only), AnChain/Sardine industry whitepapers, FinCEN 2024 guidance.

---

## Layering Rule

This is an application, not a framework. If the capability requires knowledge of financial crime, AML regulation, or SAR filing, it belongs here. If it is purely about cases, commitments, trust, or audit records, it belongs in the foundation. Never re-implement foundation primitives here.

---

## Reference Documents (in casehub-parent)

| Document | What it covers |
|----------|---------------|
| `/Users/mdproctor/claude/casehub/parent/docs/use-case-analysis.md` | Use case scoring, AML selection rationale (§8.2), compliance gap analysis |
| `/Users/mdproctor/claude/casehub/parent/docs/tutorial-strategy.md` | AML tutorial layers 1–7 (§6), layer-by-layer teaching strategy, LangChain4j framing |

---

## What casehub-aml Must Build

### Domain Model

**Investigation entities:**
- `SuspiciousTransaction` — the flagged transaction that opens a case
- `AmlInvestigationCase` — the case: `{transaction, entityGraph, patternFindings, osintFindings, riskScore, sarNarrative}`
- `SuspiciousActivityReport` — the SAR: structured filing with narrative, compliance officer sign-off, filing timestamp

**Capability tags:**
- `entity-resolution` — resolve beneficial ownership chains from flagged transaction
- `pattern-analysis` — detect layering, structuring, smurfing patterns across related transactions
- `osint-screening` — sanctions lists (OFAC/SDN), PEP databases, adverse media
- `sar-drafting` — synthesise investigation findings into SAR narrative
- `compliance-review` — compliance officer human WorkItem — review and sign SAR
- `senior-escalation` — head of compliance when officer SLA missed
- `investigation-triage` — LLM supervisor mode: select investigation path based on accumulated context

**Trust dimensions:**
- `investigation-accuracy` — SAR quality: was the SAR upheld, withdrawn, or flagged post-submission?
- `pep-clearance` — track record on politically exposed person screening specifically
- `scope-awareness` — does the agent DECLINE correctly when outside its clearance level?

### Investigation CasePlanModel (adaptive, not fixed pipeline)

Goals:
- `investigation-complete` — all required specialist findings present
- `sar-approved` — compliance officer WorkItem DONE with `file` decision
- `evidence-chain-complete` — all `causedByEntryId` links present in ledger

Key bindings:
- `entity-resolution` fires first on any new transaction — no prior analysis required
- `pattern-analysis` fires when entity graph complete
- `osint-screening` fires in parallel with pattern-analysis — simultaneous, no declaration
- `senior-analyst-required` fires if entity type is PEP or risk score > 0.8 — routing to senior, not junior
- `sar-drafting` fires when all specialist findings complete
- `compliance-officer-review` creates WorkItem with 30-day `claimDeadline` (FinCEN SLA)
- `escalate-to-head-of-compliance` fires if officer WorkItem expires
- `osint-agent-declined` handles DECLINE (agent outside clearance — immediately re-route, agent is healthy)
- `pattern-agent-failed` handles FAILURE — try backup, escalate if backup also fails

### Tutorial Structure (layer-by-layer, from tutorial-strategy.md §6)

```
Layer 1: naive Java — direct service calls, no accountability, no audit
Layer 2: + casehub-work — compliance officer WorkItem with 30-day FinCEN SLA
Layer 3: + casehub-qhorus — typed COMMAND/RESPONSE/DONE/DECLINE per specialist
Layer 4: + casehub-ledger — FinCEN audit trail, Merkle chain, GDPR Art.17 erasure
Layer 5: + casehub-engine — adaptive path (PEP routing, parallel checks, LLM triage)
Layer 6: trust routing — experienced agents on complex cases, auto-updated from SAR outcomes
Layer 7: comparison table vs IBM AMLSim and industry whitepapers
```

### Foundation Gates

| Capability | Foundation prerequisite |
|-----------|------------------------|
| Adaptive investigation paths | P0 complete (engine#186 ✅) |
| DECLINE vs FAILED routing | P0 complete |
| Parallel specialist checks | P0 complete |
| Compliance officer WorkItem | casehub-work ✅ production |
| Trust-weighted routing | P1.3 TrustWeightedSelectionStrategy wired in engine |
| LLM triage supervisor | LlmPlanningStrategy SPI (engine) |
| GDPR erasure | LedgerErasureService (casehub-ledger ✅) |
| FinCEN Merkle audit | CaseLedgerEntry ✅ (2026-04-26) |

---

## Ecosystem Conventions

**Quarkus version:** All projects use `3.32.2`. When bumping, bump all projects together.

**GitHub Packages — dependency resolution:**
```xml
<repository>
  <id>github</id>
  <url>https://maven.pkg.github.com/casehubio/*</url>
  <snapshots><enabled>true</enabled></snapshots>
</repository>
```
CI must use `server-id: github` + `GITHUB_TOKEN` in `actions/setup-java`.

**Java on this machine:**
```bash
JAVA_HOME=$(/usr/libexec/java_home -v 26)
JAVA_HOME=/Library/Java/JavaVirtualMachines/graalvm-25.jdk/Contents/Home  # native only
```

**Use `mvn` not `./mvnw`** — maven wrapper not configured on this machine.

---

## Work Tracking

**Issue tracking:** enabled
**GitHub repo:** casehubio/aml

**Automatic behaviours:**
- Before implementation begins — check for an active issue. If none, run issue-workflow Phase 1 before writing any code.
- Before any commit — confirm issue linkage.
- All commits reference an issue — `Refs #N` or `Closes #N`.
