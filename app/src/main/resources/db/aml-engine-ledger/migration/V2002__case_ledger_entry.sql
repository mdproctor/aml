-- V2002: case_ledger_entry — CaseHub audit ledger
-- Extends ledger_entry (JOINED inheritance).
-- Re-numbered from engine-ledger's V2000 to avoid collision with qhorus V2000
-- (message_ledger_entry). Casehub-aml is the consumer of casehub-engine-ledger
-- and owns this re-numbered migration. See casehub/engine#382 for origin.

CREATE TABLE case_ledger_entry (
    id           UUID         NOT NULL,
    case_id      UUID         NOT NULL,
    command_type VARCHAR(100),
    event_type   VARCHAR(100),
    case_status  VARCHAR(50),
    CONSTRAINT pk_case_ledger_entry PRIMARY KEY (id),
    CONSTRAINT fk_case_ledger_entry FOREIGN KEY (id) REFERENCES ledger_entry(id)
);

CREATE INDEX idx_cle_case_id ON case_ledger_entry (case_id);
