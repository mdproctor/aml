-- Add rejection reason column to SAR officer reviewed ledger entry.
-- Nullable — existing entries predate rejection reason capture.
ALTER TABLE aml_sar_officer_reviewed_ledger_entry
    ADD COLUMN rejection_reason VARCHAR(1000);
