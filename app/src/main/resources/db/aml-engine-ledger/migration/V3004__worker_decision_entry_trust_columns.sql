-- V2008: add trust_score_at_routing and threshold_applied to worker_decision_entry.
-- WorkerDecisionEntry in casehub-engine-ledger SNAPSHOT gained these columns for
-- trust routing evidence. Mirrors the same pattern as V2005/V2006 (tenancy_id backfill).

ALTER TABLE worker_decision_entry ADD COLUMN trust_score_at_routing DOUBLE;
ALTER TABLE worker_decision_entry ADD COLUMN threshold_applied DOUBLE;
