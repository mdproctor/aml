-- Add reconstructed and observer_failed flags to aml_trust_routing_attestation.
-- reconstructed=TRUE: written by AmlAttestationReconciler to fill an observer gap.
-- observer_failed=TRUE: written by AmlTrustRoutingObserver outer catch (failure audit record).
-- Note: a partial unique index on (investigation_case_id, capability_tag) WHERE reconstructed = TRUE
-- is desirable in production PostgreSQL to prevent multi-JVM double-writes of reconstructed entries,
-- but H2 does not support partial indexes (MODE=PostgreSQL does not include this extension).
-- The uniqueness guarantee is enforced at the service layer by AmlAttestationReconciler.
ALTER TABLE aml_trust_routing_attestation ADD COLUMN reconstructed   BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE aml_trust_routing_attestation ADD COLUMN observer_failed BOOLEAN NOT NULL DEFAULT FALSE;
