package io.casehub.aml;

import java.util.UUID;

import io.casehub.aml.domain.InvestigationSummary;
import io.casehub.aml.domain.SuspiciousTransaction;

public interface AmlInvestigator {
    /**
     * Run the AML investigation for the given transaction.
     *
     * @param transaction the flagged transaction under investigation
     * @param caseId      UUID for this investigation case; shared subjectId on all ledger entries
     */
    InvestigationSummary investigate(SuspiciousTransaction transaction, UUID caseId);
}
