CREATE TABLE aml_case_profile_ledger_entry (
    id                   UUID NOT NULL,
    flag_reason          VARCHAR(50)    NOT NULL,
    transaction_amount   DECIMAL(19,4)  NOT NULL,
    prior_incident_count INTEGER        NOT NULL,
    entity_type          VARCHAR(50),
    jurisdiction_risk    VARCHAR(50),
    network_complexity   VARCHAR(50),
    outcome              VARCHAR(50)    NOT NULL,
    confidence           DOUBLE         NOT NULL,
    investigation_path   VARCHAR(1000)  NOT NULL,
    PRIMARY KEY (id),
    FOREIGN KEY (id) REFERENCES ledger_entry(id)
);
