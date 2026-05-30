-- Layer 7: join table for AmlTrustRoutingAttestation (extends LedgerEntry via JOINED inheritance).
-- Records trust score at routing time per capability per case, written by AmlTrustRoutingObserver.
-- trustScoreAtRouting is nullable: null = no trust data was available at routing time (not zero trust).
CREATE TABLE aml_trust_routing_attestation (
    id                     UUID         NOT NULL,
    capability_tag         VARCHAR(100) NOT NULL,
    selected_worker_id     VARCHAR(255) NOT NULL,
    trust_score_at_routing DOUBLE PRECISION,
    threshold_applied      DOUBLE PRECISION NOT NULL,
    investigation_case_id  UUID         NOT NULL,
    CONSTRAINT pk_aml_trust_routing_attestation PRIMARY KEY (id),
    CONSTRAINT fk_aml_trust_routing_ledger FOREIGN KEY (id) REFERENCES ledger_entry(id)
);
