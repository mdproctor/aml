-- V2007: replace dual-use aml_investigation_ledger_entry with two dedicated sibling tables.
-- Discriminator values: AML_CASE_OPENED and AML_COMPLIANCE_REVIEW.
-- V2001 introduced the original table (dropped here). V2007 is the next available
-- number in the shared qhorus datasource version namespace (V2002-V2006 are in
-- aml-engine-ledger and aml-trust-routing migration paths).

DROP TABLE IF EXISTS aml_investigation_ledger_entry;

CREATE TABLE aml_case_opened_ledger_entry (
    id                     UUID         NOT NULL,
    transaction_id         VARCHAR(255) NOT NULL,
    origin_account_id      VARCHAR(255) NOT NULL,
    destination_account_id VARCHAR(255) NOT NULL,
    CONSTRAINT pk_aml_case_opened PRIMARY KEY (id),
    CONSTRAINT fk_aml_case_opened FOREIGN KEY (id) REFERENCES ledger_entry (id)
);

CREATE TABLE aml_compliance_review_ledger_entry (
    id      UUID         NOT NULL,
    task_id VARCHAR(255) NOT NULL,
    CONSTRAINT pk_aml_compliance_review PRIMARY KEY (id),
    CONSTRAINT fk_aml_compliance_review FOREIGN KEY (id) REFERENCES ledger_entry (id)
);
