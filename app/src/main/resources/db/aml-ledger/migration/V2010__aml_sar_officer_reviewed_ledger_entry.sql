-- Join table for AmlSarOfficerReviewedLedgerEntry (JOINED inheritance from ledger_entry).
-- Stores the officer's review decision (APPROVED or REJECTED) when they act on the SAR WorkItem.
-- The officer's actorId (human PII) is in ledger_entry.actor_id -- GDPR-erasable via LedgerErasureService.
CREATE TABLE aml_sar_officer_reviewed_ledger_entry (
    id              UUID        NOT NULL,
    review_decision VARCHAR(20) NOT NULL,
    CONSTRAINT pk_aml_sar_officer_reviewed PRIMARY KEY (id),
    CONSTRAINT fk_aml_sar_officer_reviewed_ledger FOREIGN KEY (id) REFERENCES ledger_entry(id)
);
