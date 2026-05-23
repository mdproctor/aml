-- V2001: aml_investigation_ledger_entry — AML domain audit ledger
-- Extends ledger_entry (JOINED inheritance). V1000–V1007 reserved by casehub-ledger base;
-- V2000 = qhorus agent_message_ledger_entry; V2001+ = AML consumer joins.
-- Compatible with H2 (dev/test) and PostgreSQL (production).

CREATE TABLE aml_investigation_ledger_entry (
    id             UUID         NOT NULL,
    transaction_id VARCHAR(255) NOT NULL,
    event_type     VARCHAR(50)  NOT NULL,
    CONSTRAINT pk_aml_investigation_ledger_entry PRIMARY KEY (id),
    CONSTRAINT fk_aml_investigation_ledger_entry FOREIGN KEY (id) REFERENCES ledger_entry (id)
);

CREATE INDEX idx_aml_ile_event_type ON aml_investigation_ledger_entry (event_type);
