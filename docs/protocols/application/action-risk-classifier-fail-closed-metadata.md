---
id: PP-20260610-66fc79
title: "Fail-closed ActionRiskClassifier paths must derive all gate metadata from the domain action type"
type: rule
scope: application
applies_to: "Any ActionRiskClassifier implementation in casehub-aml — specifically the missingContext / fail-closed path"
severity: important
refs:
  - ../../../api/src/main/java/io/casehub/aml/domain/AmlActionType.java
  - ../../../app/src/main/java/io/casehub/aml/routing/AmlActionRiskClassifier.java
violation_hint: "A GateRequired returned from a fail-closed path that hardcodes reversible, candidateGroups, scope, or expiresIn instead of reading them from the domain action type"
garden_ref: GE-20260610-583563
created: 2026-06-10
---

When a classifier's fail-closed path (missing context, null input, insufficient data to evaluate) must return a `GateRequired`, all gate properties — `reversible`, `candidateGroups`, `expiresIn`, and `scope` — must be read from the domain action type's own attributes (e.g. `type.reversible()`, `type.candidateGroups()`), never hardcoded. The domain enum is the single source of truth for gate policy; the fail-closed path is just a path through that policy, not a separate policy. Hardcoding defaults produces incorrect audit trails: a `SAR_FILING` gate claiming `reversible=true` tells reviewers an action is undoable when it is not.
