package io.casehub.aml.domain;

import java.util.UUID;

public record AmlInvestigationResult(
        InvestigationSummary summary,
        String complianceReviewTaskId,
        /**
         * UUID of the AML investigation case — shared subjectId for all ledger entries.
         * Null in Layer 1/2 where ledger is not yet wired.
         */
        UUID caseId,
        /**
         * UUID of the CASE_OPENED LedgerEntry written at investigation start.
         * Null in Layer 1/2 where ledger is not yet wired.
         */
        UUID ledgerCaseEntryId) {

    /** Layer 1/2 compat constructor — no ledger fields. */
    public AmlInvestigationResult(InvestigationSummary summary, String complianceReviewTaskId) {
        this(summary, complianceReviewTaskId, null, null);
    }
}
