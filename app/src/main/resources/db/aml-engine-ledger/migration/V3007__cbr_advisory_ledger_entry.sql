CREATE TABLE aml_cbr_advisory_ledger_entry (
    id UUID NOT NULL,
    case_count INT NOT NULL,
    avg_similarity DOUBLE PRECISION NOT NULL,
    confidence DOUBLE PRECISION NOT NULL,
    predominant_outcome VARCHAR(50),
    predominant_outcome_frequency DOUBLE PRECISION,
    recommended_capabilities VARCHAR(1000),
    CONSTRAINT fk_cbr_advisory_ledger_entry FOREIGN KEY (id) REFERENCES ledger_entry(id)
);
