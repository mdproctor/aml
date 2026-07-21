ALTER TABLE aml_case_profile_ledger_entry ALTER COLUMN confidence SET DATA TYPE DOUBLE PRECISION;
ALTER TABLE aml_case_profile_ledger_entry ALTER COLUMN confidence DROP NOT NULL;
