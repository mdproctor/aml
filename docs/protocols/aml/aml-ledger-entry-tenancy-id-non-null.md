---
id: PP-20260610-ae4535
title: "AML ledger entry writes must guarantee a non-null tenancyId"
type: rule
scope: repo
applies_to: "Any class that writes an AML LedgerEntry subclass — AmlTrustRoutingAttestation and any future aml-domain ledger entries"
severity: critical
refs:
  - ../../../app/src/main/java/io/casehub/aml/trust/AmlTrustRoutingObserver.java
  - ../../../app/src/main/java/io/casehub/aml/trust/AmlTrustAttestationRepository.java
violation_hint: "Passing event.tenancyId() directly to a LedgerEntry field without null-checking; entity save fails at runtime with NOT NULL constraint violation on tenancy_id"
created: 2026-06-10
---

Every site that constructs and saves an AML `LedgerEntry` subclass must ensure `tenancyId` is non-null before calling the repository. The pattern is `event.tenancyId() != null ? event.tenancyId() : TenancyConstants.DEFAULT_TENANT_ID`. The `tenancy_id` column carries a NOT NULL constraint; passing a null from an async event (where the CDI request context may be absent) fails silently until the database rejects the INSERT. This rule was enforced across `AmlTrustRoutingObserver` and `AmlTrustAttestationRepository` when casehub-ledger changed the column constraint in a SNAPSHOT.
