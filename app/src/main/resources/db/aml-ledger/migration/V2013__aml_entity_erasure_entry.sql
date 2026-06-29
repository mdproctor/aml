CREATE TABLE aml_entity_erasure_entry (
    id UUID NOT NULL,
    erased_entity_id VARCHAR(255) NOT NULL,
    erasure_reason VARCHAR(50) NOT NULL,
    memories_erased INT NOT NULL,
    CONSTRAINT pk_aml_entity_erasure_entry PRIMARY KEY (id),
    CONSTRAINT fk_aml_entity_erasure_entry_ledger
        FOREIGN KEY (id) REFERENCES ledger_entry(id)
);
